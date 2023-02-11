(ns reactive-video.core
  (:gen-class)
  (:require
    [lufs-clj.file :as lufs.file]
    [lufs-clj.filter :as lufs.filter]
    [lufs-clj.core :as lufs]
    [fivetonine.collage.core :as collage]
    [fivetonine.collage.util :as collage.util]
    [image-resizer.pad :as image-resizer.pad]
    [clojure.math :as math])
  (:import [java.awt Graphics2D Color Font]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io File]))


(defn log [& info]
  (apply println info)
  info)


(defn titles [title artist album filename]
  (let [path (str "./" filename ".png")
        file (File. path)
        width 1080
        height 1080
        image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
        graphics (.createGraphics image)
        font-size 60
        h1 (Font/createFont Font/TRUETYPE_FONT (File. "font/Arsenal-Bold.ttf"))
        h1 (.deriveFont h1 60.0)
        h2 (Font/createFont Font/TRUETYPE_FONT (File. "font/PTSans-Regular.ttf"))
        h2 (.deriveFont h2 35.0)
        padding-left 30
        padding-top 10]
    (.setColor graphics Color/WHITE)
    (.setFont graphics h1)
    (.drawString graphics title padding-left (+ padding-top 60))
    (.setFont graphics h2)
    (.drawString graphics (str artist " — " album) padding-left  (+ padding-top 115))
    (ImageIO/write image "png" file)
    path))


(defn nearest-even
  [n]
  (let [c (int (math/floor n))]
    (if (even? c) c (dec c))))


(defn -main [wav pic gain title artist album]
  (let
    [table (lufs.file/load-table wav)
     l (log "loaded audio")
     pic (collage.util/load-image pic)
     pic (collage/resize pic 
           :width 640 :height 640)
     l (log "loaded img")
     
     text (titles title artist album "target/stale/titles")
     
     gain (parse-double gain)
     
     height (.getHeight pic)
     
     data (:data table)
     sr (float (:sample-rate table))
     left (first data)
     right (second data)
     mid (map + left right)
     
     
     thresh (/ (lufs/lufs wav) -10)
     l (log "counted lufs")
     
     mid (map 
           (fn
             [s] 
             (if (> s thresh) s 0.0))
           mid)
     mid (lufs.filter/apply-filter mid (count mid) 
           {:f-type :low-pass 
            :G 0.0 
            :Q 0.3
            :fc 300.0
            :rate sr})
     l (log "applied filter")
     
     n (/ sr 25)
     for-video (partition (long n) mid)
     for-video (map (fn [a] (apply max a)) for-video)
     for-video (map #(* (abs %) gain) for-video)
     
     for-video
     (loop
       [c 0
        fv for-video
        res []]
       (if fv
         
         (let [x (first fv)
               charge 0.99
               discharge 0.06]
           (recur
             (if 
               (> x c)
               (+ (* c (- 1 charge)) (* x charge))
               (* c (- 1 discharge)))
             
             (next fv)
             (conj res c)))
         
         res))
     len (count for-video)
     l (log "applied vu")]
  
    
    
    (doall
      (map      (fn [g i]
                     (log
                       "frame" i "/" len ":"
                       (format "%.2f"
                         (* 100.0 (/ i len)))
                       "%")
                     (as-> pic p
                      (collage/scale p (+ 1 (math/log10 (+ 1 g))))
                      ((image-resizer.pad/pad-fn (/ (- 1080 (.getHeight p)) 2)) p)
                      (collage.util/save p (str "target/animation/" (format "%010d" i) ".png")))) 
        for-video
        (range (count for-video))))))


(comment
  
  
  
  (-main "audio/test.wav" "img/test.png" "1.0" "Зе биг шорт" "Брискович" "Биты 2023")
  
  
  )