(ns status-im.ui.screens.wallet-connect.session-proposal.styles
  (:require [quo.design-system.colors :as colors]))

(def acc-sheet
  {:background-color        colors/white
   :border-top-right-radius 16
   :border-top-left-radius  16
   :padding-bottom          1})

(defn toolbar-container [background-color]
  {:height 36
   :border-radius 18
   :background-color background-color
   :align-items :center
   :flex-direction :row
   :padding-left 13
   :padding-right 6})

(def toolbar-text
  {:flex-grow 1
   :margin-right 3})

(def success-sheet-container
  {:flex 1
   :align-items :center})

(def success-sheet-content
  {:flex 1
   :align-items :center})

(def success-sheet-header-container
  {:flex-direction :row
   :align-items :center
   :margin-top 10
   :margin-bottom 16})

(def dapp-logo
  {:width            120
   :height           120
   :resize-mode      :cover
   :margin-top 31
   :border-radius    16
   :border-width 2
   :border-color (:interactive-02 @colors/theme)
   :padding 5})

(def icon-container
  {})

(defn circle [default-spacing?]
  {:margin-right (if default-spacing? 4 8)})

(def sheet-body-container
  {:flex 1
   :align-items :center})

(def success-sheet-title
  {:margin-bottom 16})

(def success-sheet-button-container
  {:padding-horizontal 16
   :width "100%"
   :flex-direction :row})

(def success-sheet-button
  {:flex 1})

(def proposal-sheet-container
  {:background-color :white
   :width "100%"
   :align-items :center
   :padding-top 0
   :padding-bottom 50
   :border-top-right-radius 16
   :border-top-left-radius  16})

(def proposal-sheet-header
  {:flex-direction :row
   :align-items :center
   :justify-content :center
   :height 56
   :width "100%"
   :border-color colors/gray-lighter
   :border-bottom-width 1})

(def proposal-title-container
  {:align-items :center
   :margin-top 21})

(def proposal-title
  {})

(def proposal-description
  {:margin-vertical 16})

(def message-title
  {:margin-top 10
   :margin-bottom 14
   :margin-horizontal 72.5
   :text-align :center})

(def footer
  {:width "100%"
   :height 76
   :border-color colors/gray-lighter
   :border-top-width 1})

(def proposal-buttons-container
  {:flex 1
   :flex-direction :row
   :justify-content :space-between
   :align-items :center
   :padding-horizontal 16})

(def wallet-picker-container
  {:height 80})

(def proposal-button-left
  {})

(def proposal-button-right
  {})

(def success-button-container
  {:flex 1
   :flex-direction :row
   :justify-content :flex-end
   :align-items :center
   :padding-horizontal 16})

(defn account-container [color account-selected?]
  {:height 34
   :background-color color
   :border-radius 17
   :padding-horizontal 10
   :justify-content :center
   :margin-right 4
   :opacity (if account-selected? 1 0.5)})

(def account-selector-container
  {:height 80
   :width "100%"
   :justify-content :center
   :padding-horizontal 16
   :margin-top 40})

(def account-selector-list
  {:height 40
   :width "100%"
   :margin-top 10})

(def single-account-container
  {:width "100%"
   :align-items :center
   :padding-top 8})

(def blur-view
  {:position :absolute
   :top 80
   :left 0
   :right 0
   :bottom 0
   :background-color "rgba(255, 255, 255, 0.3)"})

(def shadow
  {:width "100%"
   :height 50
   :opacity 0.3})

(def management-sheet-header
  {:width "100%"
   :height 44
   :padding 16})

(def management-icon
  {:width 40
   :height 40
   :border-radius 20
   :margin-right 16})