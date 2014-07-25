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
  (transform [this & [options]] "Transforms a given image by the given options")
  (draw [this renderer x y] "Draw the image at the given x, y coordinates"))

(defrecord Image
    [image width height angle]
  image-actions
  (transform [this & [options]] (img/transform-image this options))
  (draw [this renderer x y] (.drawImage (:graphics renderer) image x y nil)))

(defn- create-image
  [img options]
  (let [image (->Image img (.getWidth img) (.getHeight img) 0)]
    (if options
      (img/transform-image image options)
      image)))

(defn image
  [filepath & [options]]
  (create-image (img/load-image filepath) options))

(defprotocol spritesheet-actions
  (get [this x y & [options]]))

(defrecord Spritesheet
    [image width height sprite-width sprite-height]
  spritesheet-actions
  (get [this x y & [options]]
    (create-image (. image getSubimage
                     (* x sprite-width)
                     (* y sprite-height)
                     sprite-width
                     sprite-height)
                  options)))

(defn spritesheet
  ([filepath sprite-width sprite-height]
     (let [image (img/load-image filepath)]
       (->Spritesheet image
                      (.getWidth image)
                      (.getHeight image)
                      sprite-width
                      sprite-height))))

(defprotocol game-loop-actions
  (start [this])
  (stop [this]))

(defrecord GameLoop
    [loop-fn]
  game-loop-actions
  (start [this] (when-not (:running this)
                  (assoc this :running true :thread (future (loop-fn)))))
  (stop [this] (when (:running this)
                 (assoc this :running (-> this :thread future-cancel not) :thread nil))))

(defn- create-update-loop
  [update-fn max-frame-skip tick-period]
  (fn [next-tick]
    (loop [tick next-tick updates 0]
      (if (and (> (System/nanoTime) tick)
               (< updates max-frame-skip))
        (do (update-fn)
            (recur (+ tick tick-period) (inc updates)))
        tick))))

(defn- create-sleep-or-yield
  [max-no-sleep]
  (fn [sleep-time no-sleep]
    (if (> sleep-time 0)
      (Thread/sleep (/ sleep-time 1000000N))
      (if (>= no-sleep max-no-sleep)
        (Thread/yield)
        (inc no-sleep)))))

(defn- create-game-loop
  [window update-fn render-fn {:keys [max-frame-skip tick-period max-no-sleep] :as settings}]
  (let [update-loop (create-update-loop update-fn max-frame-skip tick-period)
        sleep-or-yield (create-sleep-or-yield max-no-sleep)]
    (fn [] (loop [tick (System/nanoTime) no-sleep 0]
             (let [next-tick (update-loop tick)]
               (render-fn (get-renderer window))
               (let [new-no-sleep (or (sleep-or-yield (- next-tick (System/nanoTime)) no-sleep) 0)]
                 (recur next-tick new-no-sleep)))))))

(def game-loop-defaults {:running false
                         :ups 100
                         :max-no-sleep 8
                         :max-frame-skip 2})

(defn calculate-tick-period [ups]
  (/ 1000000000 ups))

(defn game-loop
  [window update-fn render-fn & [options]]
  (let [final-options (merge game-loop-defaults options)
        setup (merge final-options {:tick-period (calculate-tick-period (:ups final-options))})]
    (->GameLoop (create-game-loop window update-fn render-fn setup))))

(defrecord GameStatistics
    [interval-time recordings]
  (record-start-time [this time] (if (= interval-start-time 0)
                                   (swap! recordings assoc :interval-start-time time :last-time time)
                                   (swap! recordings assoc :last-time time)))
  (record-update-time [this time] (let [{:keys [last-time update-time updates]} @recordings]
                                    (swap! recordings assoc :update-time (+ update-time (- time last-time))
                                           :updates (inc updates)
                                           :last-time time)))
  (record-render-time [this time] (let [{:keys [last-time render-time renders]} @recordings]
                                    (swap! recordings assoc :render-time (+ render-time (- time last-time))
                                           :renders (inc renders)
                                           :last-time time)))
  (store-stats [this] (let [{:keys [interval-start-time last-time render-time renders update-time updates]} @recordings
                            elapsed-time (- last-time interval-start-time)]
                        (if (>= elapsed-time interval-time)
                          (swap! recordings assoc
                                 :fps (* (/ renders elapsed-time) 1000000000N)
                                 :ups (* (/ updates elapsed-time) 1000000000N)
                                 :avg-render-time (/ (/ render-time 1000000N) renders)
                                 :avg-update-time (/ (/ update-time 1000000N) updates)
                                 :updates 0
                                 :renders 0
                                 :update-time 0
                                 :render-time 0
                                 :interval-start-time 0))))
  (draw [this renderer x y & [c]] (let [graphics (:graphics renderer)
                                        original-font (.getFont graphics)
                                        original-color (.getColor graphics)
                                        {:keys [fps ups]} @recordings]
                                    (doto graphics
                                      (.setColor (or c (color :white)))
                                      (.setFont (Font. "MONOSPACED", Font/BOLD 12))
                                      (.drawString (str "FPS: " fps) x y)
                                      (.drawString (str "UPS: " ups) x (+ y 24))
                                      (.setColor original-color)
                                      (.setFont original-font)))))

(def game-statistics []
  (->GameStatistics 200000000N (atom {:interval-start-time 0
                                      :last-time 0
                                      :update-time 0
                                      :render-time 0
                                      :updates 0
                                      :renders 0}) 0 0 0 0))
