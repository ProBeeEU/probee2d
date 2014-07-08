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
  (change-size [this w h])
  (get-renderer [this])
  (render [this]))

(defrecord Window
    [window width height buffer-strategy]
  window-actions
  (show [this] (.show window))
  (change-size [this w h] (.setSize window w h))
  (get-renderer [this] (->Renderer (.getDrawGraphics buffer-strategy)
                                   width height))
  (render [this] (.show buffer-strategy))
  common-actions
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

(defn- image->buffered-image [image]
  (let [buffered-image (BufferedImage. (.getWidth image nil)
                                       (.getHeight image nil)
                                       BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics buffered-image)
      (.drawImage image 0 0 nil)
      (.dispose))
    buffered-image))

(defn- make-transparent [image c]
  (let [marker-rgb (bit-or (.getRGB (color c)) 0xFF000000)
        f (proxy [RGBImageFilter] []
            (filterRGB [x y rgb]
              (if (= (bit-or rgb 0xFF000000) marker-rgb)
                (bit-and 0x00FFFFFF rgb)
                rgb)))]
    (.createImage (Toolkit/getDefaultToolkit)
                  (FilteredImageSource. (.getSource image) f))))

(defprotocol sprite-actions
  (draw [this renderer x y]))

(defrecord Sprite
    [image width height]
  sprite-actions
  (draw [this renderer x y] (.drawImage (:graphics renderer) image x y nil)))

(defn sprite
  [filepath & [options]]
  (try
    (let [image (ImageIO/read (File. filepath))
          transparent-color (:transparent-color options)
          image (if transparent-color
                  (image->buffered-image
                   (make-transparent image transparent-color))
                  image)]
      (println transparent-color)
      (->Sprite image (.getWidth image) (.getHeight image)))
    (catch IOException e
        (. e printstacktrace))))
