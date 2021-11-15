(ns status-im.ui.screens.wallet-connect.session-proposal.styles
  (:require [quo.design-system.colors :as colors]))

(def acc-sheet
  {:background-color        colors/white
   :border-top-right-radius 16
   :border-top-left-radius  16
   :padding-bottom          1})

(defn toolbar-container [background-color]
  {:height 36
   :min-width 189
   :border-radius 18
   :background-color background-color
   :align-items :center
   :flex-direction :row
   :padding-left 12
   :padding-right 8})

(def toolbar-text
  {:margin-left 8
   :flex-grow 1})

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
  {:width            40
   :height           40
   :background-color (:interactive-02 @colors/theme)
   :resize-mode      :cover
   :border-radius    20})

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
   :padding-top 30
   :padding-bottom 80
   :border-top-right-radius 16
   :border-top-left-radius  16})

(def proposal-sheet-header
  {:flex-direction :row
   :align-items :center
   :margin-top 10
   :margin-bottom 16})

(def proposal-title-container
  {:flex-direction :row})

(def proposal-title
  {:margin-bottom 16})

(def proposal-description
  {:margin-vertical 16})

(def proposal-buttons-container
  {:padding-horizontal 16
   :width "100%"
   :align-items :stretch
   :justify-content :space-between
   :flex-direction :row
   :margin-top 6})

(def proposal-button-left
  {:flex 1
   :margin-right 4})

(def proposal-button-right
  {:flex 1
   :margin-left 4})