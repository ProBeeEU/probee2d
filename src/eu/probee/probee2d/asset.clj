(ns eu.probee.probee2d.asset
  (:require [clojure.edn :as edn]
            [eu.probee.probee2d.image :as img]))

(defmulti parse-image-def
  (fn [[id ref]] (type ref)))

(defmethod parse-image-def clojure.lang.APersistentMap
  [[id ref]]
  (hash-map id (reduce #(merge %1 (parse-image-def %2)) {} ref)))

(defmethod parse-image-def java.lang.String
  [[id ref]]
  (hash-map id (img/image ref)))

(defn load-images [definition]
  (reduce #(merge %1 (parse-image-def %2)) {} definition))

(defmulti parse-spritesheet-def
  (fn [spritesheet [id ref]] (type ref)))

(defmethod parse-spritesheet-def clojure.lang.APersistentMap
  [spritesheet [id ref]]
  (hash-map id (reduce #(merge %1 (parse-spritesheet-def spritesheet %2)) {} ref)))

(defmethod parse-spritesheet-def clojure.lang.APersistentVector
  [spritesheet [id [col row]]]
  (hash-map id (img/get spritesheet col row)))

(defn- load-spritesheet-file-from-definition
  [{:keys [file size sprite-width sprite-height]}]
  (if size
    (img/spritesheet file size)
    (img/spritesheet file sprite-width sprite-height)))

(defn- load-spritesheet-sprites-from-definition
  [definition]
  (let [spritesheet (load-spritesheet-file-from-definition definition)]
    (reduce #(merge %1 (parse-spritesheet-def spritesheet %2))
            {} (dissoc definition :file :size :sprite-width :sprite-size))))

(defn load-spritesheets [definition]
  (reduce #(merge %1 (load-spritesheet-sprites-from-definition %2))
          {} definition))

(defn load-assets [{:keys [images spritesheets]}]
  (hash-map :image (merge (load-images images)
                          (load-spritesheets spritesheets))))

(defn load-assets-from-file [file-path]
  (load-assets (edn/read-string (slurp file-path))))

(defmulti get-asset
  (fn [asset-store asset-type id] (coll? id)))

(defmethod get-asset true
  [asset-store asset-type id]
  (get-in asset-store (concat [asset-type] id)))

(defmethod get-asset false
  [asset-store asset-type id]
  (get-in asset-store (vector asset-type id)))

(defn get-image [asset-store id]
  (get-asset asset-store :image id))
