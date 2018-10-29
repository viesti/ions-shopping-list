(ns shopping-list.core
  (:require [reagent.core :as r]))

(defn main []
  [:h1 "Moi"])

(r/render [main]
  (js/document.getElementById "app"))

