(ns eu.probee.probee2d.core
  (:import (javax.swing JFrame)
           (java.awt Color Graphics Toolkit)
           (java.awt.image BufferedImage FilteredImageSource RGBImageFilter)
           (java.io File IOException)
           (javax.imageio ImageIO)))

(defmulti color
  (fn [c] (type c)))

(defmethod color clojure.lang.APersistentMap
  [{:keys [r g b]}]
  (Color. r g b))

(defmethod color clojure.lang.APersistentVector
  [[r g b]]
  (Color. r g b))

(defmethod color clojure.lang.Keyword
  [c]
  (eval (symbol (str "Color/" (name c)))))

(defmethod color java.lang.String
  [c]
  (eval (symbol (str "Color/" c))))


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
  [title width height]
  (let [window (JFrame. title)]
    (doto window
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setSize width height)
      (.setResizable false)
      (.setLocationRelativeTo nil)
      (.setVisible true)
      (.createBufferStrategy 2))
    (->Window window width height (.getBufferStrategy window))))
