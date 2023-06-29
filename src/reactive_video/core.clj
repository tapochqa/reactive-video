(ns reactive-video.core
  (:gen-class)
  (:require
    [lufs-clj.file :as lufs.file]
    [lufs-clj.filter :as lufs.filter]
    [lufs-clj.core :as lufs]
    
    [fivetonine.collage.core :as collage]
    [fivetonine.collage.util :as collage.util]
    
    [image-resizer.pad :as image-resizer.pad]
    
    [me.raynes.conch :as conch]
    
    [clojure.math :as math]
    [clojure.edn :as edn]
    
    [blurhash.core :as blurhash]
    [blurhash.encode :as blurhash.encode]
    [blurhash.decode :as blurhash.decode]
    
    )
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


(defn -main [setup]
  (let
    [
     {:keys [pic wav gain w h scale]} (edn/read-string (slurp setup))
     pic-path pic
     stamp (System/currentTimeMillis)
     temp "temp.mp4"
     background (str "bg-" stamp "." (-> pic-path (clojure.string/split #"\.") last))
     fin (str "bounce/" stamp ".mp4")
     
     
     table (lufs.file/load-table wav)
     l (log "loaded audio")
     pic (collage.util/load-image pic)
     pic (collage/resize pic 
           :width (int (/ w scale)) :height (int (/ w scale)))
     l (log "loaded img")
     
     gain (parse-double gain)
     
     height (.getHeight pic)
     
     data (:data table)
     sr (float (:sample-rate table))
     left (first data)
     right (second data)
     mid (map + left right)
     
     
     thresh (/ (lufs/integrated wav) -10)
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
     l (log "applied vu")
     
     bg 
     (-> pic-path
      
      blurhash/file->pixels
      blurhash.encode/encode
      (blurhash.decode/decode (int (/ w 70)) (int (/ h 70)))
      (blurhash/pixels->file background))
     
     bg
     (collage.util/save
       (collage/resize 
         (collage.util/load-image background) 
         {:width w
          :height h})
       background)

     l (log "generated bg")
     
     
     
     ]
    
    

    
    
    
    (conch/with-programs [mkdir rm ffmpeg]
      
      (rm "-rf" "target/animation")
      (rm "-f" temp)
      
      (mkdir "target/animation")
      (mkdir "target/animation/auto")
      
      (doall
        (map      (fn [g i]
                       (log
                         "frame" i "/" len ":"
                         (format "%.2f"
                           (* 100.0 (/ i len)))
                         "%")
                       (as-> pic p
                        (collage/scale p (+ 1 (math/log10 (+ 1 g))))
                        (collage/paste
                          
                          (collage.util/load-image background)
                          
                          p 
                          (int (math/floor (/ (- w (.getWidth p))   2)))
                          (int (math/ceil  (/ (- h (.getHeight p))  2))))
                        ; ((image-resizer.pad/pad-fn (/ (- 1080 (.getHeight p)) 2)) p)
                        (collage.util/save p (str "target/animation/" (format "%010d" i) ".png")))) 
          for-video
          (vec (range (count for-video)))))
      
     (println "Generating final video...")
      
      (ffmpeg 
        "-y"
        "-framerate" 25
        "-pattern_type" "glob"
        "-i" "target/animation/*.png"
        "-i" wav
        "-c:v" "libx264"
        "-pix_fmt" "yuv420p"
        "-b:a" "320k"
        fin)
      
      (rm "-rf" "target/animation")
      (rm background)
      (println fin)
      (shutdown-agents))))


(comment
  
  (def image
    (blurhash/file->pixels "img/test.png"))
  
  
  (-main "setup.edn")

  (time (collage.util/load-image "img/blurred2-.png"))
  
  (-> "img/e49549f829.jpg"
    
    blurhash/file->pixels
    blurhash.encode/encode
    (blurhash.decode/decode 32 10)
    (blurhash/pixels->file "img/blurred2-.png")
    
    )
  
  
  
  
  (lufs.file/load-table "audio/test.wav")
  
  (-main "setup.edn"))