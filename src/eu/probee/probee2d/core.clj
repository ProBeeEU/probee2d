(ns eu.probee.probee2d.core
  (:import (javax.swing JFrame)
           (java.awt Graphics Toolkit Font Rectangle)
           (java.awt.event KeyAdapter KeyEvent))
  (:require [clojure.string :as str]
            [eu.probee.probee2d.util :refer [color]]
            [eu.probee.probee2d.input :refer [pressed?] :as input]))

(defprotocol common-actions
  (dispose [this]))

(defprotocol renderer-actions
  (clear [this c])
  (set-color [this c])
  (draw-rect [this x y width height]))

(defrecord Renderer
    [graphics width height]
  renderer-actions
  (clear [this c] (doto graphics
                    (.setColor (color c))
                    (.fillRect 0 0 width height)))
  (set-color [this c] (.setColor graphics (color c)))
  (draw-rect [thix x y width height] (.fill graphics (Rectangle. x y width height)))
  common-actions
  (dispose [this] (.dispose graphics)))

(defprotocol window-actions
  (show [this])
  (hide [this])
  (change-size [this w h])
  (get-input-device [this device-type])
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
  (get-input-device [this device-type] (get-in this [:inputs device-type]))
  (get-renderer [this] (->Renderer (.getDrawGraphics buffer-strategy)
                                   width height))
  (render [this] (.show buffer-strategy))
  common-actions
  (dispose [this] (do (.dispose window)
                      nil)))

(defn window
  ([title width height]
     (let [w (JFrame. title)]
       (doto w
         (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
         (.setSize width height)
         (.setResizable false)
         (.setLocationRelativeTo nil)
         (.setVisible true)
         (.createBufferStrategy 2))
       (->Window w width height (.getBufferStrategy w) true)))
  ([title width height input-devices]
     (reduce #(assoc-in %1 [:inputs %2] (input/add-device %1 %2)) (window title width height) input-devices)))

(defprotocol game-statistics-actions
  (record-start [this])
  (record-update [this])
  (record-render [this])
  (store-stats [this])
  (draw-stats
    [this renderer x y]
    [this renderer x y c]
    "Draw the statistics with the given renderer at the specified location"))

(defrecord GameStatistics
    [interval-time recordings]
  game-statistics-actions
  (record-start [this] (let [time (System/nanoTime)]
                         (if (= (:interval-start-time @recordings) 0)
                           (swap! recordings assoc :interval-start-time time :last-time time)
                           (swap! recordings assoc :last-time time))))
  (record-update [this] (let [time (System/nanoTime)
                              {:keys [last-time update-time updates]} @recordings]
                          (swap! recordings assoc :update-time (+ update-time (- time last-time))
                                 :updates (inc updates)
                                 :last-time time)))
  (record-render [this] (let [time (System/nanoTime)
                              {:keys [last-time render-time renders]} @recordings]
                          (swap! recordings assoc :render-time (+ render-time (- time last-time))
                                 :renders (inc renders)
                                 :last-time time)))
  (store-stats [this] (let [{:keys [interval-start-time last-time render-time renders update-time updates]} @recordings
                            elapsed-time (- last-time interval-start-time)]
                        (if (>= elapsed-time interval-time)
                          (swap! recordings assoc
                                 :fps (format "%.2f" (double (* (/ renders elapsed-time) 1000000000N)))
                                 :ups (format "%.2f" (double (* (/ updates elapsed-time) 1000000000N)))
                                 :avg-render-time (format "%.4f" (double (/ (/ render-time 1000000N) renders)))
                                 :avg-update-time (format "%.4f" (double (/ (/ update-time 1000000N) updates)))
                                 :updates 0
                                 :renders 0
                                 :update-time 0
                                 :render-time 0
                                 :interval-start-time 0))))
  (draw-stats [this renderer x y] (draw-stats this renderer x y nil))
  (draw-stats [this renderer x y c] (let [graphics (:graphics renderer)
                                              original-font (.getFont graphics)
                                              original-color (.getColor graphics)
                                              {:keys [fps avg-render-time ups avg-update-time]} @recordings]
                                          (doto graphics
                                            (.setColor (or c (color :white)))
                                            (.setFont (Font. "MONOSPACED", Font/BOLD 12))
                                            (.drawString (str "FPS: " fps) x y)
                                            (.drawString (str "UPS: " ups) x (+ y 14))
                                            (.drawString (str "Avg. render: " avg-render-time " ms") x (+ y 28))
                                            (.drawString (str "Avg. update: " avg-update-time " ms") x (+ y 42))
                                            (.setColor original-color)
                                            (.setFont original-font)))))

(defn game-statistics [& [interval]]
  (->GameStatistics (* (or interval 1) 1000000000N)
                    (atom {:interval-start-time 0
                           :last-time 0
                           :update-time 0
                           :render-time 0
                           :updates 0
                           :renders 0})))

(defmacro create-update-loop
  [update-fn max-frame-skip tick-period & [stats]]
  `(fn [next-tick#]
     (loop [tick# next-tick# updates# 0]
       (if (and (> (System/nanoTime) tick#)
                (< updates# ~max-frame-skip))
         (do (try (~update-fn)
                  (catch Exception e#
                    (println (str "caught exception: " (.getMessage e#)))
                    (throw e#)))
             (when ~stats (record-update ~stats))
             (recur (+ tick# ~tick-period) (inc updates#)))
         tick#))))

(defn- create-sleep-or-yield
  [max-no-sleep]
  (fn [sleep-time no-sleep]
    (if (> sleep-time 0)
      (Thread/sleep (/ sleep-time 1000000N))
      (if (>= no-sleep max-no-sleep)
        (Thread/yield)
        (inc no-sleep)))))

(defmacro create-game-loop
  [window update-fn render-fn settings & [stats]]
  `(let [update-loop# (create-update-loop ~update-fn
                                          (:max-frame-skip ~settings)
                                          (:tick-period ~settings) ~stats)
         sleep-or-yield# (create-sleep-or-yield (:max-no-sleep ~settings))]
     (fn [] (loop [tick# (System/nanoTime) no-sleep# 0]
              (when ~stats (record-start ~stats))
              (let [next-tick# (update-loop# tick#)
                    renderer# (get-renderer ~window)]
                (try (~render-fn renderer#)
                  (catch Exception e#
                    (println (str "caught exception: " (.getMessage e#)))
                    (throw e#)))
                (dispose renderer#)
                (render ~window)
                (when ~stats (record-render ~stats))
                (let [new-no-sleep# (or (sleep-or-yield# (- next-tick# (System/nanoTime)) no-sleep#) 0)]
                  (when ~stats (store-stats ~stats))
                  (recur next-tick# new-no-sleep#)))))))

(def game-loop-defaults {:ups 100
                         :max-no-sleep 8
                         :max-frame-skip 2})

(defn calculate-tick-period [ups]
  (/ 1000000000 ups))

(defmacro wrap-fn [f]
  `(fn [& args#] (apply ~(resolve f) args#)))

(defmacro game-loop
  [window update-fn render-fn & [options stats]]
  `(let [final-options# (merge game-loop-defaults ~options)
         setup# (merge final-options# {:tick-period (calculate-tick-period (:ups final-options#))})]
     (future ((create-game-loop ~window ~update-fn ~render-fn setup# ~stats)))))

(defn stop-game-loop
  [game-loop]
  (future-cancel game-loop))

(defmacro game-frame
  [title width height input-devices update-fn render-fn & [options use-stats]]
  `(let [stats# (when ~use-stats (game-statistics (or (:statistics-interval ~options) 1)))
         w# (assoc (window ~title ~width ~height ~input-devices) :stats stats#)]
    (assoc w# :game-loop (game-loop w# ~update-fn ~render-fn ~options stats#))))
