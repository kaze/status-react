(ns status-im.wallet-connect.core
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.ethereum.core :as ethereum]
            [status-im.utils.fx :as fx]
            [status-im.signing.core :as signing]
            [status-im.utils.wallet-connect :as wallet-connect]
            [status-im.browser.core :as browser]
            [taoensso.timbre :as log]
            [status-im.async-storage.core :as async-storage]
            [status-im.utils.config :as config]))

(fx/defn switch-wallet-connect-enabled
  {:events [:multiaccounts.ui/switch-wallet-connect-enabled]}
  [{:keys [db]} enabled?]
  (when enabled?
    (wallet-connect/init
     #(re-frame/dispatch [:wallet-connect/client-init %])
     #(log/error "[wallet-connect]" %)))
  {::async-storage/set! {:wallet-connect-enabled? enabled?}
   :db (cond-> db
         (not enabled?)
         (dissoc :wallet-connect/client)
         :always
         (assoc :wallet-connect/enabled? enabled?))})

(fx/defn proposal-handler
  {:events [:wallet-connect/proposal]}
  [{:keys [db] :as cofx} request-event]
  (let [proposal (js->clj request-event :keywordize-keys true)
        proposer (:proposer proposal)
        metadata (:metadata proposer)]
    {:db (assoc db :wallet-connect/proposal proposal :wallet-connect/proposal-metadata metadata)
     :show-wallet-connect-sheet nil}))

(fx/defn session-connected
  {:events [:wallet-connect/created]}
  [{:keys [db]} session]
  (let [session (js->clj session :keywordize-keys true)]
    (log/debug "[wallet connect] session created - " session)
    {:show-wallet-connect-success-sheet nil
     :db (assoc db :wallet-connect/session-connected session)}))

(fx/defn deleted-handler
  {:events [:wallet-connect/deleted]}
  [{:keys [db] :as cofx} request-event]
  (log/debug "[wallet connect] session deleted - " (js->clj request-event)))

(fx/defn request-handler
  {:events [:wallet-connect/request]}
  [{:keys [db] :as cofx} request-event]
  (let [request (js->clj request-event :keywordize-keys true)
        params (:request request)
        pending-requests (or (:wallet-connect/pending-requests db) [])
        new-pending-requests (conj pending-requests request)
        client (get db :wallet-connect/client)
        topic (:topic request)]
    {:db (assoc db :wallet-connect/pending-requests new-pending-requests)
     :dispatch [:wallet-connect/request-received request]}))

(defn subscribe-to-events [wallet-connect-client]
  (.on wallet-connect-client (wallet-connect/session-request-event) #(re-frame/dispatch [:wallet-connect/request %]))
  (.on wallet-connect-client (wallet-connect/session-created-event) #(re-frame/dispatch [:wallet-connect/created %]))
  (.on wallet-connect-client (wallet-connect/session-deleted-event) #(re-frame/dispatch [:wallet-connect/deleted %]))
  (.on wallet-connect-client (wallet-connect/session-proposal-event) #(re-frame/dispatch [:wallet-connect/proposal %])))

(fx/defn approve-proposal
  {:events [:wallet-connect/approve-proposal]}
  [{:keys [db]} account]
  (let [client (get db :wallet-connect/client)
        proposal (get db :wallet-connect/proposal)
        topic (:topic proposal)
        permissions (:permissions proposal)
        blockchain (:blockchain permissions)
        proposal-chain-ids (map #(last (string/split % #":")) (:chains blockchain))
        available-chain-ids (map #(get-in % [:config :NetworkId]) (vals (get db :networks/networks)))
        supported-chain-ids (filter (fn [chain-id] #(boolean (some #{chain-id} available-chain-ids))) proposal-chain-ids)
        address (:address account)
        accounts (map #(str "eip155:" % ":" (ethereum/normalized-hex address)) supported-chain-ids)
        ;; TODO: Check for unsupported
        metadata (get db :wallet-connect/proposal-metadata)
        response {:state {:accounts accounts}
                  :metadata config/default-wallet-connect-metadata}
        active-topics (or (:wallet-connect/active-topics db) {})
        new-active-topics (assoc active-topics topic proposal)]
    (-> ^js client
        (.approve (clj->js {:proposal proposal :response response}))
        (.then #(log/debug "[wallet-connect] session proposal approved"))
        (.catch #(log/error "[wallet-connect] session proposal approval error:" %)))
    {:hide-wallet-connect-sheet nil
     :db (assoc db :wallet-connect/active-topics new-active-topics)}))

(fx/defn reject-proposal
  {:events [:wallet-connect/reject-proposal]}
  [{:keys [db]} account]
  (let [client (get db :wallet-connect/client)
        proposal (get db :wallet-connect/proposal)]
    (-> ^js client
        (.reject (clj->js {:proposal proposal}))
        (.then #(log/debug "[wallet-connect] session proposal rejected"))
        (.catch #(log/error "[wallet-connect] " %)))
    {:hide-wallet-connect-sheet nil}))

(fx/defn change-session-account
  {:events [:wallet-connect/change-session-account]}
  [{:keys [db]} topic account]
  (let [client (get db :wallet-connect/client)
        proposal (get db :wallet-connect/proposal)]
    (-> ^js client
        (.update (clj->js {:topic topic
                           :account account}))
        (.then #(log/debug "[wallet-connect] session topic " topic " changed to account " account))
        (.catch #(log/error "[wallet-connect] " %)))
    {}))

(fx/defn disconnect-session
  {:events [:wallet-connect/disconnect-session]}
  [{:keys [db]} topic reason]
  (let [client (get db :wallet-connect/client)]
    (-> ^js client
        (.disconnect (clj->js {:topic topic :reason reason}))
        (.then #(log/debug "[wallet-connect] session disconnected - topic " topic))
        (.catch #(log/error "[wallet-connect] " %)))
    {:dispatch [:bottom-sheet/hide]
     :db (assoc db :wallet-connect/sessions "session data here")}))

(fx/defn pair-session
  {:events [:wallet-connect/pair]}
  [{:keys [db]} {:keys [data]}]
  (let [client (get db :wallet-connect/client)
        wallet-connect-enabled? (get db :wallet-connect/enabled?)]
    (when wallet-connect-enabled?
      (.pair client (clj->js {:uri data})))
    (merge
     {:dispatch [:navigate-back]}
     (when wallet-connect-enabled?
       {:db (assoc db :wallet-connect/scanned-uri data)}))))

(fx/defn wallet-connect-client-initate
  {:events [:wallet-connect/client-init]}
  [{:keys [db] :as cofx} client]
  (subscribe-to-events client)
  {:db (assoc db :wallet-connect/client client)})

(fx/defn wallet-connect-complete-transaction
  {:events [:wallet-connect.dapp/transaction-on-result]}
  [{:keys [db]} message-id topic result]
  (let [client (get db :wallet-connect/client)
        response {:topic topic
                  :response {:jsonrpc "2.0"
                             :id message-id
                             :result result}}]
    (.respond client (clj->js response))
    {:db (assoc db :wallet-connect/response response)}))

(fx/defn wallet-connect-send-async
  [cofx {:keys [method params id] :as payload} message-id topic]
  (let [message?      (browser/web3-sign-message? method)
        dapps-address (get-in cofx [:db :multiaccount :dapps-address])
        accounts (get-in cofx [:db :multiaccount/visible-accounts])
        typed? (and (not= constants/web3-personal-sign method) (not= constants/web3-eth-sign method))]
    (if (or message? (= constants/web3-send-transaction method))
      (let [[address data] (cond (and (= method constants/web3-keycard-sign-typed-data)
                                      (not (vector? params)))
                                 ;; We don't use signer argument for keycard sign-typed-data
                                 ["0x0" params]
                                 message? (browser/normalize-sign-message-params params typed?)
                                 :else [nil nil])]
        (when (or (not message?) (and address data))
          (signing/sign cofx (merge
                              (if message?
                                {:message {:address address
                                           :data data
                                           :v4 (= constants/web3-sign-typed-data-v4 method)
                                           :typed? typed?
                                           :pinless? (= method constants/web3-keycard-sign-typed-data)
                                           :from address}}
                                {:tx-obj  (-> params
                                              first
                                              (update :from #(or % dapps-address))
                                              (dissoc :gasPrice))})
                              {:on-result [:wallet-connect.dapp/transaction-on-result message-id topic]
                               :on-error  [:wallet-connect.dapp/transaction-on-error message-id topic]}))))
      (when (#{"eth_accounts" "eth_coinbase"} method)
        (wallet-connect-complete-transaction cofx message-id topic (if (= method "eth_coinbase") dapps-address [dapps-address]))))))

(def permissioned-method
  #{"eth_accounts" "eth_coinbase" "eth_sendTransaction" "eth_sign"
    "keycard_signTypedData"
    "eth_signTypedData" "personal_sign" "personal_ecRecover"})

(defn has-permissions? [{:dapps/keys [permissions]} dapp-name method]
  (boolean
   (and (permissioned-method method)
        (not (some #{constants/dapp-permission-web3} (get-in permissions [dapp-name :permissions]))))))

(fx/defn wallet-connect-send-async-read-only
  [{:keys [db] :as cofx} {:keys [method] :as payload} message-id topic]
  (wallet-connect-send-async cofx payload message-id topic))

(fx/defn process-request
  {:events [:wallet-connect/request-received]}
  [{:keys [db] :as cofx} session-request]
  (let [pending-requests (get db :wallet-connect/pending-requests)
        {:keys [topic request]} session-request
        {:keys [id]} request]
    (wallet-connect-send-async-read-only cofx request id topic)))
