(ns status-im.ui.screens.wallet-connect.session-proposal.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.ui.components.react :as react]
            [status-im.i18n.i18n :as i18n]
            [status-im.utils.security]
            [quo.design-system.colors :as colors]
            [quo.core :as quo]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.ui.screens.browser.accounts :as accounts]
            [status-im.ui.components.bottom-panel.views :as bottom-panel]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.screens.wallet-connect.session-proposal.styles :as styles]))

(defn circle [{:keys [color size style icon icon-size icon-color]}]
  [react/view
   {:style (merge
            style
            {:width            size
             :height           size
             :background-color color
             :border-radius    (/ size 2)
             :align-items      :center
             :justify-content  :center})}
   (when icon
     [icons/icon icon
      (merge
       {:color    (if icon-color icon-color colors/blue)}
       icon-size)])])

(def billfold-icon-container-width 18)

(def billfold-icon-container-height 17.8)

(def chevron-icon-container-width 24)

(def chevron-icon-container-height 24)

(defn toolbar-selection [{:keys [text background-color on-press]}]
  [react/touchable-opacity {:on-press on-press}
   [react/view {:style (styles/toolbar-container background-color)}
    [icons/icon
     :main-icons/billfold
     {:color  (:text-05 @colors/theme)
      :width  billfold-icon-container-width
      :height billfold-icon-container-height}]
    [quo/text {:color :inverse
               :style styles/toolbar-text}
     text]
    [icons/icon
     :main-icons/chevron-down
     {:color  (:text-05 @colors/theme)
      :width  chevron-icon-container-width
      :height chevron-icon-container-height}]]])

(def tiny-circle-size 4)

(def big-circle-size 24)

(defview success-sheet-view []
  (letsubs [{:keys [name icons]} [:wallet-connect/proposal-metadata]
            dapps-account [:dapps-account]]
    (let [icon-uri (when (and icons (> (count icons) 0)) (first icons))]
      [react/view styles/success-sheet-container
       [react/view styles/success-sheet-content
        [react/view styles/success-sheet-header-container
         [react/image {:style styles/dapp-logo
                       :source {:uri icon-uri}}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle true)}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle true)}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle false)}]
         [circle {:color (:positive-03 @colors/theme)
                  :size  big-circle-size
                  :style (styles/circle false)
                  :icon :main-icons/checkmark
                  :icon-size {:width 16 :height 16}
                  :icon-color colors/white-persist}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle true)}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle true)}]
         [circle {:color (:interactive-02 @colors/theme)
                  :size  tiny-circle-size
                  :style (styles/circle true)}]
         [chat-icon/custom-icon-view-list (:name dapps-account) (:color dapps-account)]]]
       [react/view styles/sheet-body-container
        [react/view {:style {:flex-direction :row}}
         [quo/text {:weight :bold
                    :size   :large}
          (str name " ")]
         [quo/text {:weight :regular
                    :size   :large
                    :color  :secondary
                    :style  styles/success-sheet-title}
          (i18n/label :t/wallet-connect-app-connected)]]
        [react/view styles/success-sheet-button-container
         [react/view styles/success-sheet-button
          [quo/button
           {:theme :monocromatic
            :on-press #(re-frame/dispatch [:bottom-sheet/hide])}
           (i18n/label :t/wallet-connect-go-back)]]]]])))

(defn session-proposal-sheet [{:keys [name icons]}]
  (let [visible-accounts @(re-frame/subscribe [:visible-accounts-without-watch-only])
        dapps-account @(re-frame/subscribe [:dapps-account])
        icon-uri (when (and icons (> (count icons) 0)) (first icons))
        accounts  @(re-frame/subscribe [:multiaccount/visible-accounts])]
    [react/view {:style styles/acc-sheet}
     [react/view styles/proposal-sheet-container
      [react/view styles/proposal-sheet-header
       [react/image {:style styles/dapp-logo
                     :source {:uri icon-uri}}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle true)}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle true)}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle false)}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  big-circle-size
                :style (styles/circle false)
                :icon :main-icons/checkmark
                :icon-size {:width 16 :height 16}}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle true)}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle true)}]
       [circle {:color (:interactive-02 @colors/theme)
                :size  tiny-circle-size
                :style (styles/circle true)}]
       [chat-icon/custom-icon-view-list (:name dapps-account) (:color dapps-account)]]
      [react/view styles/sheet-body-container
       [react/view {:style styles/proposal-title-container}
        [quo/text {:weight :bold
                   :size   :large}
         (str name " ")]
        [quo/text {:weight :regular
                   :size   :large
                   :color  :secondary
                   :style  styles/proposal-title}
         (i18n/label :t/wallet-connect-proposal-title)]]
       [toolbar-selection {:icon :main-icons/billfold
                           :background-color (:color dapps-account)
                           :text (:name dapps-account)
                           :multiaccounts accounts
                           :on-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                          {:content (accounts/accounts-list visible-accounts dapps-account)}])}]
       [quo/text {:align :center
                  :color :secondary
                  :style styles/proposal-description}
        (i18n/label :t/wallet-connect-proposal-description {:name name})]
       [react/view styles/proposal-buttons-container
        [react/view styles/proposal-button-left
         [quo/button
          {:on-press #(re-frame/dispatch [:wallet-connect/reject-proposal])}
          (i18n/label :t/cancel)]]
        [react/view styles/proposal-button-right
         [quo/button
          {:theme     :accent
           :on-press  #(re-frame/dispatch [:wallet-connect/approve-proposal dapps-account])}
          (i18n/label :t/connect)]]]]]]))

(def success-sheet
  {:content success-sheet-view})

(def bottom-sheet
  {:content success-sheet-view})

(defview wallet-connect-proposal-sheet []
  (letsubs [proposal-metadata [:wallet-connect/proposal-metadata]]
    [bottom-panel/animated-bottom-panel
     proposal-metadata
     session-proposal-sheet
     #(re-frame/dispatch [:hide-wallet-connect-sheet])]))
