(ns eu.probee.probee2d.image
  (:import (java.awt GraphicsEnvironment Toolkit)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.awt.image BufferedImage FilteredImageSource RGBImageFilter
                           AffineTransformOp)
           (java.io File IOException)
           (javax.imageio ImageIO))
  (:require [eu.probee.probee2d.util :refer [color]]))

(defn- create-compatible-image [{:keys [image width height]}]
  (let [graphics-configuration (.. (GraphicsEnvironment/getLocalGraphicsEnvironment)
                                   getDefaultScreenDevice getDefaultConfiguration)
        compatible-image (. graphics-configuration createCompatibleImage
                            width height (. image getTransparency))]
    (doto (. compatible-image createGraphics)
      (. drawImage image 0 0 nil)
      (. dispose))
    compatible-image))

(defn- image->buffered-image [image]
  (let [buffered-image (BufferedImage. (.getWidth image nil)
                                       (.getHeight image nil)
                                       BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics buffered-image)
      (.drawImage image 0 0 nil)
      (.dispose))
    buffered-image))

(defn make-transparent [image c]
  (let [marker-rgb (bit-or (.getRGB (color c)) 0xFF000000)
        f (proxy [RGBImageFilter] []
            (filterRGB [x y rgb]
              (if (= (bit-or rgb 0xFF000000) marker-rgb)
                (bit-and 0x00FFFFFF rgb)
                rgb)))]
    (assoc image
      :image (image->buffered-image
              (.createImage (Toolkit/getDefaultToolkit)
                            (FilteredImageSource. (.getSource (:image image)) f))))))

(defn- get-image-dimensions [image]
  (hash-map :width (.getWidth image)
            :height (.getHeight image)))

(defn load-image [filepath]
  (try
    (ImageIO/read (File. filepath))
    (catch IOException e
      (. e printstacktrace))))

(defn scale [image factor]
  (let [new-image (.filter (AffineTransformOp. (doto (AffineTransform.)
                                                 (.scale factor factor))
                                               AffineTransformOp/TYPE_BILINEAR)
                           (:image image) nil)]
    (merge (assoc image :image new-image) (get-image-dimensions new-image))))

(defn- find-translation
  "Helper function used, to ensure right translation of image when rotating"
  [transformation image]
  (doto (AffineTransform.)
    (.translate (* (.getX (.transform transformation
                                     (Point2D$Double. 0 (:height image)) nil)) -1)
               (* (.getY (.transform transformation (Point2D$Double. 0 0) nil)) -1))))

(defn rotate [{:keys [angle width height image] :as img} angle-degrees]
  "Rotate given image the given degrees by the clock"
  (let [new-image (.filter (AffineTransformOp.
                            (doto (AffineTransform.)
                              (.rotate (Math/toRadians angle-degrees)
                                       (/ width 2)
                                       (/ height 2))
                              (#(.preConcatenate % (find-translation % img))))
                            AffineTransformOp/TYPE_BILINEAR)
                           image nil)]
    (merge (assoc img
             :image new-image
             :angle (mod (+ angle angle-degrees) 360))
           (get-image-dimensions new-image))))

(defn flip [{:keys [width height image] :as img} & directions]
  (let [horizontal? (some #{:horizontal} directions)
        vertical? (some #{:vertical} directions)]
    (assoc img
      :image (.filter (AffineTransformOp.
                       (doto (AffineTransform/getScaleInstance
                              (if horizontal? -1 1)
                              (if vertical? -1 1))
                         (.translate (if horizontal? (* width -1) 0)
                                     (if vertical? (* height -1) 0)))
                       AffineTransformOp/TYPE_BILINEAR) image nil))))

(defn transform-image [image {:keys [transparent-color scale-factor
                                     rotate-angle flip-direction] :as options}]
  (reduce #(%2 %1) image
          (keep #(or %)
                (vector (when transparent-color
                          #(make-transparent % transparent-color))
                        (when flip-direction
                          #(flip % flip-direction))
                        (when rotate-angle
                          #(rotate % rotate-angle))
                        (when scale-factor
                          #(scale % scale-factor))))))
