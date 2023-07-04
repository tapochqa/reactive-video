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
    [clojure.java.io :as io]
    [clojure.string :as str]
    
    [blurhash.core :as blurhash]
    [blurhash.encode :as blurhash.encode]
    [blurhash.decode :as blurhash.decode]
    
    
    [yandex-music.core :as yandex-music]
    
    )
  (:import [java.awt Graphics2D Color Font]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io File]))



(defn delete-directory-recursive
  "Recursively delete a directory."
  [^java.io.File file]

  (when (.isDirectory file)
    (run! delete-directory-recursive (.listFiles file)))

  (io/delete-file file))



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




(defn -main 
  
  
  ([setup ffmpeg-path]
   (-main nil setup ffmpeg-path))
  
  
  ([yandex-link setup ffmpeg-path]
   
   
    (let
      [setup (edn/read-string (slurp setup))
       
       setup
       (if (some? yandex-link)
         
         (let [y-track-id (->> (str/split yandex-link #"track/")
                       last
                       parse-long)
         
               y-cover-path "bounce/cover.jpeg"
               y-track-path (yandex-music/dl! setup y-track-id "bounce")
               
               ]
           
           (yandex-music/save-file! (yandex-music/track-cover-link setup y-track-id) y-cover-path)
           
             
             (into
               setup
               {:pic y-cover-path
                :wav y-track-path}))
         
         setup)
       {:keys [pic wav gain w h scale]} setup 

       pic-path pic
       stamp (System/currentTimeMillis)
       temp "bounce/temp.mp4"
       cover-path (str "bounce/" stamp "." (-> pic-path (clojure.string/split #"\.") last))
       background (str "bounce/bg-" stamp "." (-> pic-path (clojure.string/split #"\.") last))
       fin (str "bounce/" stamp ".mp4")
       
       frames-done! (atom 0.0)
       
       table (lufs.file/load-table wav)
       l (log "loaded audio")
       pic (collage.util/load-image pic)
       pic (collage/resize pic 
             :width (int (/ w scale)) :height (int (/ w scale)))
       cover (collage.util/save pic cover-path)
       
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
       l (log "applied filter 1")
       
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
       (-> cover-path
        
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
      
      

      
      
      
      (conch/let-programs [ffmpeg ffmpeg-path]
        
        
        (if-not
          (.exists (io/file "bounce/animation"))
            (.mkdir (java.io.File. "bounce/animation")))
        
        
        (doall
          (pmap      (fn [g i]
                       
                       (let [path (str "bounce/animation/" (format "%010d" i) ".png")]
                       
                         (if (.exists (io/file path))
                           
                           (log i "✔ ")
                           
                           (do 
                             
                             (reset! frames-done! (inc @frames-done!))
                             (log
                               " "
                               (format "%.2f"
                                 (* 100.0 (/ @frames-done! len)))
                               "% ")
                             (as-> pic p
                              (collage/scale p (+ 1 (math/log10 (+ 1 g))))
                              (collage/paste
                                
                                (collage.util/load-image background)
                                
                                p 
                                (int (math/floor (/ (- w (.getWidth p))   2)))
                                (int (math/ceil  (/ (- h (.getHeight p))  2))))
                              ; ((image-resizer.pad/pad-fn (/ (- 1080 (.getHeight p)) 2)) p)
                              (collage.util/save p path))))))
            for-video
            (vec (range (count for-video)))))
        
       (io/delete-file background)
       (io/delete-file cover-path)
        
       (log "Generating video...")
        
        (ffmpeg 
          "-y"
          "-framerate" 25
          "-pattern_type" "glob"
          "-i" "bounce/animation/*.png"
          "-i" wav
          "-c:v" "libx264"
          "-pix_fmt" "yuv420p"
          "-b:a" "320k"
          temp)
        
        (delete-directory-recursive (File. "bounce/animation"))
        
        
        (log "Offsetting audio...")
        (ffmpeg
          "-i" temp
          "-itsoffset" 0.1
          "-i" temp
          "-map" "0:v"
          "-map" "1:a"
          "-c" "copy"
          fin)
        
        
        (io/delete-file temp)

        (log fin)
        (shutdown-agents)))))


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
  
  (.exists (io/file "font"))
  (lufs.file/load-table "audio/test.wav")
  
  (-main "setup.edn"))