(ns eu.probee.probee2d.util
  (:import (java.awt Color)))

(def colors {:black (Color/black)
             :blue (Color/blue)
             :cyan (Color/cyan)
             :dark-gray (Color/darkGray)
             :gray (Color/gray)
             :green (Color/green)
             :light-gray (Color/lightGray)
             :magenta (Color/magenta)
             :orage (Color/orange)
             :pink (Color/pink)
             :red (Color/red)
             :white (Color/white)
             :yellow(Color/yellow)})

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
  (c colors))

(defmethod color java.lang.String
  [c]
  (color (keyword c)))
