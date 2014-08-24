(ns eu.probee.probee2d.input
  (:import (java.awt.event KeyAdapter KeyEvent))
  (:require [clojure.string :as str]))

(defn- key-name [key-code]
  (keyword (str/lower-case (KeyEvent/getKeyText key-code))))

(defmulti key-pressed
  (fn [key-states key] (type key)))

(defmethod key-pressed clojure.lang.Keyword
  [key-states key]
  (true? (key key-states)))

(defmethod key-pressed java.lang.Character
  [key-states key]
  (key-pressed key-states (keyword (str key))))

(defmethod key-pressed java.lang.String
  [key-states key]
  (key-pressed key-states (keyword key)))

(defmethod key-pressed java.lang.Number
  [key-states key]
  (key-pressed key-states (key-name key)))

(defmethod key-pressed clojure.lang.APersistentVector
  [key-states keys]
  (every? #(key-pressed key-states %) keys))

(defprotocol PKeyboard
  (pressed? [this key]))

(defrecord Keyboard
    [key-states]
  PKeyboard
  (pressed? [this key] (key-pressed @key-states key)))

(defn add-keyboard
  [{:keys [window]}]
  (let [pressed-keys (atom {})]
    (doto window
      (.setFocusTraversalKeysEnabled false)
      (.addKeyListener (proxy [KeyAdapter] []
                         (keyPressed [event]
                           (swap! pressed-keys assoc
                                  (key-name (.getKeyCode event)) true))
                         (keyReleased [event]
                           (swap! pressed-keys assoc
                                  (key-name (.getKeyCode event)) false)))))
    (->Keyboard pressed-keys)))

(defn add-device
  [window device-type]
  (case device-type
    :keyboard (add-keyboard window)
    nil))
