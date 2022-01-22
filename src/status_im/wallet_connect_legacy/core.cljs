(ns status-im.wallet-connect-legacy.core
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.ethereum.core :as ethereum]
            [status-im.utils.fx :as fx]
            [status-im.signing.core :as signing]
            [status-im.utils.wallet-connect-legacy :as wallet-connect-legacy]
            [status-im.browser.core :as browser]
            [taoensso.timbre :as log]
            [status-im.async-storage.core :as async-storage]
            [status-im.utils.config :as config]))

(fx/defn proposal-handler
  {:events [:wallet-connect-legacy/proposal]}
  [{:keys [db] :as cofx} request-event connector]
  (let [proposal (js->clj request-event :keywordize-keys true)
        params (first (:params proposal))
        metadata (merge (:peerMeta params) {:wc-version 1})
        chain-id (:chainId params)]
    (println proposal params metadata "LELLELELELELLELE")
    {:db (assoc db :wallet-connect-legacy/proposal-connector connector :wallet-connect-legacy/proposal-chain-id chain-id :wallet-connect/proposal-metadata metadata)
     :show-wallet-connect-sheet nil}))

(fx/defn session-connected
  {:events [:wallet-connect-legacy/created]}
  [{:keys [db]} session]
  (let [connector (get db :wallet-connect-legacy/proposal-connector)
        session (merge (js->clj session :keywordize-keys true) {:wc-version 1
                                                                :connector connector})
        params (first (:params session))
        metadata (:peerMeta params)
        account (first (:accounts params))
        sessions (get db :wallet-connect-legacy/sessions)
        updated-sessions (if sessions (conj sessions session) [session])]
    (println "[wallet connect 1.0] session created - " session)
    {:show-wallet-connect-success-sheet nil
     :db (assoc db :wallet-connect/session-connected session :wallet-connect-legacy/sessions updated-sessions)}))

(fx/defn manage-app
  {:events [:wallet-connect-legacy/manage-app]}
  [{:keys [db]} session]
  {:db (assoc db :wallet-connect/session-managed session :wallet-connect/showing-app-management-sheet? true)
   :show-wallet-connect-app-management-sheet nil})

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
  {:show-wallet-connect-sheet nil})

(defn subscribe-to-events [wallet-connect-legacy-client]
  ;; (.on wallet-connect-legacy-client (wallet-connect/session-request-event) #(re-frame/dispatch [:wallet-connect-legacy/request %]))
  ;; (.on wallet-connect-legacy-client (wallet-connect/session-created-event) #(re-frame/dispatch [:wallet-connect-legacy/created %]))
  ;; (.on wallet-connect-legacy-client (wallet-connect/session-deleted-event) #(re-frame/dispatch [:wallet-connect-legacy/update-sessions]))
  ;; (.on wallet-connect-legacy-client (wallet-connect/session-updated-event) #(re-frame/dispatch [:wallet-connect-legacy/update-sessions]))
  ;; (.on wallet-connect-legacy-client (wallet-connect/session-proposal-event) #(re-frame/dispatch [:wallet-connect-legacy/proposal %]))
  )

(fx/defn approve-proposal
  {:events [:wallet-connect-legacy/approve-proposal]}
  [{:keys [db]} account]
  (let [connector (get db :wallet-connect-legacy/proposal-connector)
        proposal-chain-id (get db :wallet-connect-legacy/proposal-chain-id)
        address (ethereum/normalized-hex (:address account))
        accounts [address]]
    (^js .approveSession connector (clj->js {:accounts accounts :chainId proposal-chain-id}))
    {:hide-wallet-connect-sheet nil}))

(fx/defn reject-proposal
  {:events [:wallet-connect-legacy/reject-proposal]}
  [{:keys [db]} account]
  (let [connector (get db :wallet-connect-legacy/proposal-connector)]
    (^js .rejectSession connector)
    {:hide-wallet-connect-sheet nil}))

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
    {:db (assoc db :wallet-connect/showing-app-management-sheet? false)
     :hide-wallet-connect-app-management-sheet nil}))

(fx/defn disconnect-session
  {:events [:wallet-connect-legacy/disconnect]}
  [{:keys [db]} topic]
  (let [client (get db :wallet-connect-legacy/client)]
    (-> ^js client
        (.disconnect (clj->js {:topic topic}))
        (.then #(log/debug "[wallet-connect-legacy] session disconnected - topic " topic))
        (.catch #(log/error "[wallet-connect-legacy] " %)))
    {:hide-wallet-connect-app-management-sheet nil
     :hide-wallet-connect-success-sheet nil
     :db (-> db
             (assoc :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))
             (dissoc :wallet-connect/session-managed))}))

(fx/defn pair-session
  {:events [:wallet-connect-legacy/pair]}
  [{:keys [db]} {:keys [data]}]
  (let [connector (wallet-connect-legacy/create-connector data)
        wallet-connect-enabled? (get db :wallet-connect/enabled?)]
    (when wallet-connect-enabled?
      (do
        (^js .on connector "session_request" (fn [error payload]
                                               (re-frame/dispatch [:wallet-connect-legacy/proposal payload connector])))
        (^js .on connector "connect" (fn [error payload]
                                       (println payload "SESSION CREATED!!!")
                                       (re-frame/dispatch [:wallet-connect-legacy/created payload])))))
    (println connector "DLELLE")
    (merge
     {:dispatch [:navigate-back]}
     (when wallet-connect-enabled?
       {:db (assoc db :wallet-connect-legacy/scanned-uri data)}))))

(fx/defn update-sessions
  {:events [:wallet-connect-legacy/update-sessions]}
  [{:keys [db] :as cofx}]
  (let [client (get db :wallet-connect-legacy/client)]
    {:db (-> db
             (assoc :wallet-connect-legacy/sessions (js->clj (.-values (.-session client)) :keywordize-keys true))
             (dissoc :wallet-connect/session-managed))}))

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
