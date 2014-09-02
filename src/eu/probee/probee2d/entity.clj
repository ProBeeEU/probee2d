(ns eu.probee.probee2d.entity
  (:import (java.awt Rectangle))
  (:require [eu.probee.probee2d.image :as img]))

(defstruct Sprite :x :y :img :vel-x :vel-y)

(defn sprite [x y img & [vel-x vel-y]]
  (struct Sprite x y img (or vel-x 0) (or vel-y 0)))

(defn move-sprite [{:keys [x y vel-x vel-y] :as sprite}]
  (assoc sprite :x (+ x vel-x) :y (+ y vel-y)))

(defn draw-sprite [{:keys [x y img] :as sprite} renderer]
  (let [{:keys [width height]} img]
    (img/draw img renderer (- x (/ width 2)) (- y (/ height 2)))))
