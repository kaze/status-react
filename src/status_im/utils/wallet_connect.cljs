(ns status-im.utils.wallet-connect
  (:require ["@walletconnect/client" :refer [CLIENT_EVENTS] :default WalletConnectClient]
            ["@react-native-community/async-storage" :default AsyncStorage]
            [clojure.string :as string]))

(def default-relay-provider "wss://relay.walletconnect.org")

(def default-metadata {:name "Status Wallet"
                       :description "Status is a secure messaging app, crypto wallet, and Web3 browser built with state of the art technology."
                       :url "#"
                       :icons ["https://statusnetwork.com/img/press-kit-status-logo.svg"]})

(defn init [on-success on-error]
  (-> ^js WalletConnectClient
      (.init (clj->js {:controller true
                       :relayProvider default-relay-provider
                       :logger "debug"
                       :metadata default-metadata
                       :storageOptions {:asyncStorage ^js AsyncStorage}}))
      (.then on-success)
      (.catch on-error)))

(defn session-request-event [] (.-request (.-session CLIENT_EVENTS)))

(defn session-created-event [] (.-created (.-session CLIENT_EVENTS)))

(defn session-deleted-event [] (.-deleted (.-session CLIENT_EVENTS)))

(defn session-proposal-event [] (.-proposal (.-session CLIENT_EVENTS)))

(defn url? [url]
  (string/starts-with? url "wc:"))