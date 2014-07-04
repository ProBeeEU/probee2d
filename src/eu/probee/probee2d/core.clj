(ns eu.probee.probee2d.core
  (:import (javax.swing JFrame)))

(defn create-window
  [{:keys [title width height]}]
  (let [window (JFrame. title)]
    (doto window
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setSize width height)
      (.setResizable false)
      (.setLocationRelativeTo nil)
      (.setVisible true))
    {:window window :buffer-strategy (.createBufferStrategy window 2)
     :height height :width width}))

(defn change-window-size
  [window {:keys [width height]}]
  (.setSize (:window window) width height))
