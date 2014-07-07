(ns eu.probee.probee2d.core
  (:import (javax.swing JFrame)))

(defprotocol window-actions
  (show [this])
  (change-size [this w h])
  (get-graphics [this])
  (dispose [this]))

(defrecord Window
    [window width height buffer-strategy]
  window-actions
  (show [this] (.show window))
  (change-size [this w h] (.setSize window w h))
  (get-graphics [this] (.getDrawGraphics buffer-strategy))
  (dispose [this] (.dispose window)))

(defn window
  [{:keys [title width height]}]
  (let [window (JFrame. title)]
    (doto window
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setSize width height)
      (.setResizable false)
      (.setLocationRelativeTo nil)
      (.setVisible true)
      (.createBufferStrategy 2))
    (->Window window width height (.getBufferStrategy window))))
