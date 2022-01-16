(ns status-im.wallet-connect-legacy.core
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

(fx/defn proposal-handler
  {:events [:wallet-connect-legacy/proposal]}
  [{:keys [db] :as cofx} request-event]
  (let [proposal (js->clj request-event :keywordize-keys true)
        proposer (:proposer proposal)
        metadata (:metadata proposer)]
    {:db (assoc db :wallet-connect-legacy/proposal proposal :wallet-connect-legacy/proposal-metadata metadata)
     :show-wallet-connect-legacy-sheet nil}))

(fx/defn session-connected
  {:events [:wallet-connect-legacy/created]}
  [{:keys [db]} session]
  (let [session (js->clj session :keywordize-keys true)
        client (get db :wallet-connect-legacy/client)]
    (log/debug "[wallet connect] session created - " session)
    {:show-wallet-connect-legacy-success-sheet nil
     :db (assoc db :wallet-connect-legacy/session-connected session :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))}))

(fx/defn manage-app
  {:events [:wallet-connect-legacy/manage-app]}
  [{:keys [db]} session]
  (let [session (js->clj session :keywordize-keys true)]
    {:db (assoc db :wallet-connect-legacy/session-managed session :wallet-connect-legacy/showing-app-management-sheet? true)
     :show-wallet-connect-legacy-app-management-sheet nil}))

(fx/defn request-handler
  {:events [:wallet-connect-legacy/request]}
  [{:keys [db] :as cofx} request-event]
  (let [request (js->clj request-event :keywordize-keys true)
        params (:request request)
        pending-requests (or (:wallet-connect-legacy/pending-requests db) [])
        new-pending-requests (conj pending-requests request)
        client (get db :wallet-connect-legacy/client)
        topic (:topic request)]
    {:db (assoc db :wallet-connect-legacy/pending-requests new-pending-requests)
     :dispatch [:wallet-connect-legacy/request-received request]}))

(fx/defn request-handler-test
  {:events [:wallet-connect-legacy/request-test]}
  [{:keys [db] :as cofx}]
  {:show-wallet-connect-legacy-sheet nil})

(defn subscribe-to-events [wallet-connect-legacy-client]
  (.on wallet-connect-legacy-client (wallet-connect-legacy/session-request-event) #(re-frame/dispatch [:wallet-connect-legacy/request %]))
  (.on wallet-connect-legacy-client (wallet-connect-legacy/session-created-event) #(re-frame/dispatch [:wallet-connect-legacy/created %]))
  (.on wallet-connect-legacy-client (wallet-connect-legacy/session-deleted-event) #(re-frame/dispatch [:wallet-connect-legacy/update-sessions]))
  (.on wallet-connect-legacy-client (wallet-connect-legacy/session-updated-event) #(re-frame/dispatch [:wallet-connect-legacy/update-sessions]))
  (.on wallet-connect-legacy-client (wallet-connect-legacy/session-proposal-event) #(re-frame/dispatch [:wallet-connect-legacy/proposal %])))

(fx/defn approve-proposal
  {:events [:wallet-connect-legacy/approve-proposal]}
  [{:keys [db]} account]
  (let [client (get db :wallet-connect-legacy/client)
        proposal (get db :wallet-connect-legacy/proposal)
        topic (:topic proposal)
        permissions (:permissions proposal)
        blockchain (:blockchain permissions)
        proposal-chain-ids (map #(last (string/split % #":")) (:chains blockchain))
        available-chain-ids (map #(get-in % [:config :NetworkId]) (vals (get db :networks/networks)))
        supported-chain-ids (filter (fn [chain-id] #(boolean (some #{chain-id} available-chain-ids))) proposal-chain-ids)
        address (:address account)
        accounts (map #(str "eip155:" % ":" (ethereum/normalized-hex address)) supported-chain-ids)
        ;; TODO: Check for unsupported
        metadata (get db :wallet-connect-legacy/proposal-metadata)
        response {:state {:accounts accounts}
                  :metadata config/default-wallet-connect-legacy-metadata}]
    (-> ^js client
        (.approve (clj->js {:proposal proposal :response response}))
        (.then #(log/debug "[wallet-connect-legacy] session proposal approved"))
        (.catch #(log/error "[wallet-connect-legacy] session proposal approval error:" %)))
    {:hide-wallet-connect-legacy-sheet nil}))

(fx/defn reject-proposal
  {:events [:wallet-connect-legacy/reject-proposal]}
  [{:keys [db]} account]
  (let [client (get db :wallet-connect-legacy/client)
        proposal (get db :wallet-connect-legacy/proposal)]
    (-> ^js client
        (.reject (clj->js {:proposal proposal}))
        (.then #(log/debug "[wallet-connect-legacy] session proposal rejected"))
        (.catch #(log/error "[wallet-connect-legacy] " %)))
    {:hide-wallet-connect-legacy-sheet nil}))

(fx/defn change-session-account
  {:events [:wallet-connect-legacy/change-session-account]}
  [{:keys [db]} topic account]
  (let [client (get db :wallet-connect-legacy/client)
        sessions (get db :wallet-connect-legacy/sessions)
        session (first (filter #(= (:topic %) topic) sessions))
        permissions (:permissions session)
        blockchain (:blockchain permissions)
        proposal-chain-ids (map #(last (string/split % #":")) (:chains blockchain))
        address (:address account)
        available-chain-ids (map #(get-in % [:config :NetworkId]) (vals (get db :networks/networks)))
        supported-chain-ids (filter (fn [chain-id] #(boolean (some #{chain-id} available-chain-ids))) proposal-chain-ids)
        accounts (map #(str "eip155:" % ":" (ethereum/normalized-hex address)) supported-chain-ids)]
    (-> ^js client
        (.update (clj->js {:topic topic
                           :state {:accounts accounts}}))
        (.then #(log/debug "[wallet-connect-legacy] session topic " topic " changed to account " account))
        (.catch #(log/error "[wallet-connect-legacy] " %)))
    {:db (assoc db :wallet-connect-legacy/showing-app-management-sheet? false)
     :hide-wallet-connect-legacy-app-management-sheet nil}))

(fx/defn disconnect-session
  {:events [:wallet-connect-legacy/disconnect]}
  [{:keys [db]} topic]
  (let [client (get db :wallet-connect-legacy/client)]
    (-> ^js client
        (.disconnect (clj->js {:topic topic}))
        (.then #(log/debug "[wallet-connect-legacy] session disconnected - topic " topic))
        (.catch #(log/error "[wallet-connect-legacy] " %)))
    {:hide-wallet-connect-legacy-app-management-sheet nil
     :hide-wallet-connect-legacy-success-sheet nil
     :db (-> db
             (assoc :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))
             (dissoc :wallet-connect-legacy/session-managed))}))

(fx/defn pair-session
  {:events [:wallet-connect-legacy/pair]}
  [{:keys [db]} {:keys [data]}]
  (let [client (get db :wallet-connect-legacy/client)
        wallet-connect-legacy-enabled? (get db :wallet-connect-legacy/enabled?)]
    (when wallet-connect-legacy-enabled?
      (.pair client (clj->js {:uri data})))
    (merge
     {:dispatch [:navigate-back]}
     (when wallet-connect-legacy-enabled?
       {:db (assoc db :wallet-connect-legacy/scanned-uri data)}))))

(fx/defn wallet-connect-legacy-client-initate
  {:events [:wallet-connect-legacy/client-init]}
  [{:keys [db] :as cofx} client]
  (subscribe-to-events client)
  {:db (assoc db :wallet-connect-legacy/client client :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))})

(fx/defn update-sessions
  {:events [:wallet-connect-legacy/update-sessions]}
  [{:keys [db] :as cofx}]
  (let [client (get db :wallet-connect-legacy/client)]
    {:db (-> db
             (assoc :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))
             (dissoc :wallet-connect-legacy/session-managed))}))

(fx/defn wallet-connect-legacy-complete-transaction
  {:events [:wallet-connect-legacy.dapp/transaction-on-result]}
  [{:keys [db]} message-id topic result]
  (let [client (get db :wallet-connect-legacy/client)
        response {:topic topic
                  :response {:jsonrpc "2.0"
                             :id message-id
                             :result result}}]
    (.respond client (clj->js response))
    {:db (assoc db :wallet-connect-legacy/response response)}))

(fx/defn wallet-connect-legacy-send-async
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
                              {:on-result [:wallet-connect-legacy.dapp/transaction-on-result message-id topic]
                               :on-error  [:wallet-connect-legacy.dapp/transaction-on-error message-id topic]}))))
      (when (#{"eth_accounts" "eth_coinbase"} method)
        (wallet-connect-legacy-complete-transaction cofx message-id topic (if (= method "eth_coinbase") dapps-address [dapps-address]))))))

(def permissioned-method
  #{"eth_accounts" "eth_coinbase" "eth_sendTransaction" "eth_sign"
    "keycard_signTypedData"
    "eth_signTypedData" "personal_sign" "personal_ecRecover"})

(defn has-permissions? [{:dapps/keys [permissions]} dapp-name method]
  (boolean
   (and (permissioned-method method)
        (not (some #{constants/dapp-permission-web3} (get-in permissions [dapp-name :permissions]))))))

(fx/defn wallet-connect-legacy-send-async-read-only
  [{:keys [db] :as cofx} {:keys [method] :as payload} message-id topic]
  (wallet-connect-legacy-send-async cofx payload message-id topic))

(fx/defn process-request
  {:events [:wallet-connect-legacy/request-received]}
  [{:keys [db] :as cofx} session-request]
  (let [pending-requests (get db :wallet-connect-legacy/pending-requests)
        {:keys [topic request]} session-request
        {:keys [id]} request]
    (wallet-connect-legacy-send-async-read-only cofx request id topic)))
