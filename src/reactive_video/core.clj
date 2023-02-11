(ns reactive-video.core
  (:gen-class)
  (:require
    [lufs-clj.file :as lufs.file]
    [lufs-clj.filter :as lufs.filter]
    [lufs-clj.core :as lufs]
    [fivetonine.collage.core :as collage]
    [fivetonine.collage.util :as collage.util]
    [image-resizer.pad :as image-resizer.pad]
    [clojure.math :as math]))


(defn log [& info]
  (apply println info)
  info)

(defn nearest-even
  [n]
  (let [c (int (math/floor n))]
    (if (even? c) c (dec c))))


(defn -main [wav pic gain]
  (let
    [table (lufs.file/load-table wav)
     l (log "loaded audio")
     
     pic (collage.util/load-image pic)
     pic (collage/crop pic 
           0 0 
           (nearest-even (.getWidth pic))
           (nearest-even (.getHeight pic)))
     l (log "loaded img")
     
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
               discharge 0.02]
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
      (map-indexed (fn [i g]
                     (log "frame" i "/" len)
                     (as-> pic p
                      (collage/scale p (+ 1 (math/log10 (+ 1 g))))
                      ((image-resizer.pad/pad-fn (- (nearest-even (* height (+ gain 1))) (.getHeight p))) p)
                      (collage.util/save p (str "target/animation/" (format "%010d" i) ".png")))) for-video))))


(comment
  
  
  
  (-main "test.wav" "test.png" 1)
  
  
  )