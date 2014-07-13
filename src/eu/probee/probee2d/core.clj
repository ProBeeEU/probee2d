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

(defn- image->buffered-image [image]
  (let [buffered-image (BufferedImage. (.getWidth image nil)
                                       (.getHeight image nil)
                                       BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics buffered-image)
      (.drawImage image 0 0 nil)
      (.dispose))
    buffered-image))

(defn- convert-to-transparent [image c]
  (let [marker-rgb (bit-or (.getRGB (color c)) 0xFF000000)
        f (proxy [RGBImageFilter] []
            (filterRGB [x y rgb]
              (if (= (bit-or rgb 0xFF000000) marker-rgb)
                (bit-and 0x00FFFFFF rgb)
                rgb)))]
    (.createImage (Toolkit/getDefaultToolkit)
                  (FilteredImageSource. (.getSource image) f))))

(defn- get-image-dimensions [image]
  (hash-map :width (.getWidth image)
            :height (.getHeight image)))

(defn- load-image [filepath & [options]]
  (let [image (ImageIO/read (File. filepath))
        transparent-color (:transparent-color options)]
    (if transparent-color
      (image->buffered-image
       (convert-to-transparent image transparent-color))
      image)))

(defprotocol image-actions
  (make-transparent [this c] "Make the given color transparent in the image")
  (draw [this renderer x y] "Draw the image at the given x, y coordinates"))

(defrecord Image
    [image width height]
  image-actions
  (make-transparent [this c] (assoc this :image
                                    (image->buffered-image (convert-to-transparent image c))))
  image-actions
  (draw [this renderer x y] (.drawImage (:graphics renderer) image x y nil)))

(defn image
  [filepath & [options]]
  (try
    (let [image (load-image filepath options)]
      (->Image image (.getWidth image) (.getHeight image)))
    (catch IOException e
        (. e printstacktrace))))

(defprotocol spritesheet-actions
  (get [this x y]))

(defrecord Spritesheet
    [image width height sprite-width sprite-height]
  image-actions
  (make-transparent [this c] (assoc this :image
                                    (image->buffered-image (convert-to-transparent image c))))
  spritesheet-actions
  (get [this x y] (->Image (. image getSubimage
                               (* x sprite-width)
                               (* y sprite-height)
                               sprite-width
                               sprite-height)
                            sprite-width
                            sprite-height)))

(defn spritesheet
  ([filepath sprite-width sprite-height & [options]]
     (try
       (let [image (load-image filepath options)]
         (->Spritesheet image
                        (.getWidth image)
                        (.getHeight image)
                        sprite-width
                        sprite-height))
       (catch IOException e
         (. e printstacktrace)))))
