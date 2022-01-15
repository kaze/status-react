(ns status-im.utils.wallet-connect-legacy
  (:require ["@walletconnect/client-legacy" :refer [CLIENT_EVENTS] :default WalletConnectLegacyClient]
            ["@react-native-community/async-storage" :default AsyncStorage]
            [clojure.string :as string]
            [status-im.utils.config :as config]))

(defn url? [url]
  (string/starts-with? url "wc:"))