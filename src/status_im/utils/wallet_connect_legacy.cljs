(ns status-im.utils.wallet-connect-legacy
  (:require ["@walletconnect/client-legacy" :default WalletConnect]
            ["@react-native-community/async-storage" :default AsyncStorage]
            [clojure.string :as string]
            [status-im.utils.config :as config]))

;; new WalletConnect(
;;   {
;;     // Required
;;     uri: "wc:8a5e5bdc-a0e4-47...TJRNmhWJmoxdFo6UDk2WlhaOyQ5N0U=",
;;     // Required
;;     clientMeta: {
;;       description: "WalletConnect Developer App",
;;       url: "https://walletconnect.org",
;;       icons: ["https://walletconnect.org/walletconnect-logo.png"],
;;       name: "WalletConnect",
;;     },
;;   },
;;   {
;;     // Optional
;;     url: "<YOUR_PUSH_SERVER_URL>",
;;     type: "fcm",
;;     token: token,
;;     peerMeta: true,
;;     language: language,
;;   }
;; )

(defn create-connector [uri]
  (println (clj->js {{:uri uri}
                     {:clientMeta config/default-wallet-connect-metadata}}) "dsdsdsdds")
  (WalletConnect.
   (clj->js {:uri uri
             :clientMeta config/default-wallet-connect-metadata})))

(defn url? [url]
  (string/starts-with? url "wc:"))