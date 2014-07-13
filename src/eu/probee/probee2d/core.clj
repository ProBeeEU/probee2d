(ns eu.probee.probee2d.core
  (:import (javax.swing JFrame)
           (java.awt Graphics Toolkit))
  (:require [eu.probee.probee2d.image :as img]
            [eu.probee.probee2d.util :refer [color]]))

(defprotocol common-actions
  (dispose [this]))

(defprotocol renderer-actions
  (clear [this c]))

(defrecord Renderer
    [graphics width height]
  renderer-actions
  (clear [this c] (doto graphics
                    (.setColor (color c))
                    (.fillRect 0 0 width height)))
  common-actions
  (dispose [this] (.dispose graphics)))

(defprotocol window-actions
  (show [this])
  (hide [this])
  (change-size [this w h])
  (get-renderer [this])
  (render [this]))

(defrecord Window
    [window width height buffer-strategy visible]
  window-actions
  (show [this] (do (.setVisible window true)
                   (assoc this :visible true)))
  (hide [this] (do (.setVisible window false)
                   (assoc this :visible false)))
  (change-size [this w h] (do (.setSize window w h)
                              (assoc this :width w :height h)))
  (get-renderer [this] (->Renderer (.getDrawGraphics buffer-strategy)
                                   width height))
  (render [this] (.show buffer-strategy))
  common-actions
  (dispose [this] (do (.dispose window)
                      nil)))

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
    (->Window window width height (.getBufferStrategy window) true)))

(defprotocol image-actions
  (draw [this renderer x y] "Draw the image at the given x, y coordinates"))

(defrecord Image
    [image width height angle]
  image-actions
  (draw [this renderer x y] (.drawImage (:graphics renderer) image x y nil)))

(defn image
  [filepath & [options]]
  (let [img (img/load-image filepath)
        image (->Image img (.getWidth img) (.getHeight img) 0)]
    (if options
      (img/transform-image image options)
      image)))

(defprotocol spritesheet-actions
  (get [this x y]))

(defrecord Spritesheet
    [image width height sprite-width sprite-height]
  spritesheet-actions
  (get [this x y] (->Image (. image getSubimage
                              (* x sprite-width)
                              (* y sprite-height)
                              sprite-width
                              sprite-height)
                           sprite-width
                           sprite-height
                           0)))

(defn spritesheet
  ([filepath sprite-width sprite-height]
     (let [image (img/load-image filepath)]
       (->Spritesheet image
                      (.getWidth image)
                      (.getHeight image)
                      sprite-width
                      sprite-height))))
