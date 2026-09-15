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
   
    [cheshire.core :as json]

    [clojure.math :as math]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.java.process :as process]

    [blurhash.core :as blurhash]
    [blurhash.encode :as blurhash.encode]
    [blurhash.decode :as blurhash.decode]

    [yandex-music.core :as yandex-music]

    [virtuoso.core :as virtuoso])
  
  (:import [java.awt Graphics2D Color Font RenderingHints]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io File BufferedOutputStream]
           [java.nio ByteBuffer ByteOrder]))

;; ---------------------------------------------------------------------------
;; helpers
;; ---------------------------------------------------------------------------

(defn delete-directory-recursive [^java.io.File file]
  (when (.isDirectory file)
    (run! delete-directory-recursive (.listFiles file)))
  (io/delete-file file))

(defn log [& info]
  (apply println info)
  info)

(defn nearest-even [n]
  (let [c (int (math/floor n))]
    (if (even? c) c (dec c))))

(defn repeat-each [n coll]
  (vec (mapcat (fn [x] (repeat n x)) coll)))

;; ---------------------------------------------------------------------------
;; title card
;; ---------------------------------------------------------------------------

(defn titles [title artist album filename]
  (let [path (str "./" filename ".png")
        file (File. path)
        width 1080
        height 1080
        image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
        graphics (.createGraphics image)
        h1 (-> (Font/createFont Font/TRUETYPE_FONT (File. "font/Arsenal-Bold.ttf"))
               (.deriveFont 60.0))
        h2 (-> (Font/createFont Font/TRUETYPE_FONT (File. "font/PTSans-Regular.ttf"))
               (.deriveFont 35.0))
        padding-left 30
        padding-top 10]
    (.setColor graphics Color/WHITE)
    (.setFont graphics h1)
    (.drawString graphics title padding-left (+ padding-top 60))
    (.setFont graphics h2)
    (.drawString graphics (str artist " — " album) padding-left (+ padding-top 115))
    (ImageIO/write image "png" file)
    path))

;; ---------------------------------------------------------------------------
;; fast BufferedImage -> raw RGB24 bytes
;; ---------------------------------------------------------------------------

(defn ^"[B" image->rgb24
  "Convert a BufferedImage to a packed RGB24 byte array.
   Uses bulk getRGB for speed instead of per-pixel calls."
  [^BufferedImage img]
  (let [w (.getWidth img)
        h (.getHeight img)
        n (* w h)
        pixels (int-array n)
        out (byte-array (* n 3))]
    ;; bulk read
    (.getRGB img 0 0 w h pixels 0 w)
    (dotimes [i n]
      (let [rgb (aget pixels i)
            o (* i 3)]
        (aset out o       (unchecked-byte (bit-shift-right rgb 16)))
        (aset out (inc o) (unchecked-byte (bit-shift-right rgb 8)))
        (aset out (+ o 2) (unchecked-byte rgb))))
    out))

;; ---------------------------------------------------------------------------
;; render one frame at a given scale, on top of a shared background image
;; ---------------------------------------------------------------------------

(defn render-frame
  ^BufferedImage [^BufferedImage bg ^BufferedImage pic s w h]
  (let [s (double s)
        w (int w)
        h (int h)
        scaled (collage/scale pic s)
        sw (.getWidth ^BufferedImage scaled)
        sh (.getHeight ^BufferedImage scaled)
        out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        g (.createGraphics out)]
    (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                       RenderingHints/VALUE_INTERPOLATION_BILINEAR)
    (.drawImage g bg 0 0 nil)
    (.drawImage g ^BufferedImage scaled
                (int (math/floor (/ (- w sw) 2)))
                (int (math/ceil  (/ (- h sh) 2)))
                nil)
    (.dispose g)
    out))

;; ---------------------------------------------------------------------------
;; -main
;; ---------------------------------------------------------------------------

(defn -main
  ([setup ffmpeg-path]
   (-main nil setup ffmpeg-path))

  ([yandex-link setup ffmpeg-path]
   (let [setup (edn/read-string (slurp setup))
         setup (if (some? yandex-link)
                 (let [y-track-id (->> (str/split yandex-link #"track/") last parse-long)
                       y-cover-path "bounce/cover.jpeg"
                       y-track-path (yandex-music/dl! setup y-track-id "bounce")]
                   (yandex-music/save-file!
                     (yandex-music/track-cover-link setup y-track-id)
                     y-cover-path)
                   (into setup {:pic y-cover-path :wav y-track-path}))
                 setup)

         {:keys [pic wav gain w h scale]} setup
         w (int w)
         h (int h)

         pic-path pic
         stamp (System/currentTimeMillis)
         cover-path (str "bounce/" stamp "." (-> pic-path (clojure.string/split #"\.") last))
         background (str "bounce/bg-" stamp "." (-> pic-path (clojure.string/split #"\.") last))
         fin (str "bounce/" stamp ".mp4")

         frames-done! (atom 0.0)

         table (lufs.file/load-table wav)
         _ (log "loaded audio")
         
         data (:data table)
         sr (float (:sample-rate table))
         left (first data)
         right (second data)
         mid (map + left right)
         
         mu
         (json/parse-string
            (process/exec "mu-cli" wav) true)
         _ (log "understood music")
         
         drums-mapped
         (map :value (get-in mu [:instrumentActivity :activity :drum]))
         
         bass-mapped
         (map :value (get-in mu [:instrumentActivity :activity :bass]))
         
         
         drums-over-table
         (repeat-each (-> sr int (/ 20)) drums-mapped)
         
         bass-over-table
         (repeat-each (-> sr int (/ 20)) drums-mapped)
         
         
         energy
         (map (fn [b d] (+ (/ 4.0 (- 1.0 b)) (/ 10.0 (- 1.0 d)))) 
           bass-over-table
           drums-over-table)
         

         pic (collage.util/load-image pic)
         pic (collage/resize pic :width (int (/ w scale)) :height (int (/ w scale)))
         _ (collage.util/save pic cover-path)
         _ (log "loaded img")

         gain (parse-double gain)
         height (.getHeight pic)

         n (/ sr 25)
         for-video (->> energy
                        (partition (long n))
                        (map (fn [a] (apply max a)))
                        (map #(* % gain)))

         
         len (count for-video)
         _ (log "applied vu")

         bg (-> cover-path
                blurhash/file->pixels
                blurhash.encode/encode
                (blurhash.decode/decode (int (/ w 70)) (int (/ h 70)))
                (blurhash/pixels->file background))
         bg (collage.util/save
              (collage/resize (collage.util/load-image background)
                              {:width w :height h})
              background)
         _ (log "generated bg")

         ;; Preload the background as a BufferedImage once
         bg-img (collage.util/load-image background)

         ;; Precompute scale factors for each frame
         scales (double-array
         (mapv #(double (+ 1.0 (math/log10 (+ 1.0 (double %))))) for-video))

         total (count for-video)]

     (conch/let-programs [ffmpeg ffmpeg-path]
       (log "Encoding video (direct pipe)...")

       ;; Single FFmpeg process:
       ;;   - raw RGB24 frames on stdin
       ;;   - wav audio from file
       ;;   - audio offset applied on the input side
       ;;   - x264 encode in one pass
       (let [proc (.exec (Runtime/getRuntime)
                         (into-array String
                           [ffmpeg-path
                            "-y"
                            "-f" "rawvideo"
                            "-pix_fmt" "rgb24"
                            "-s" (str w "x" h)
                            "-r" "25"
                            "-i" "-"
                            "-itsoffset" "0"
                            "-i" wav
                            "-map" "0:v"
                            "-map" "1:a"
                            "-c:v" "libx264"
                            "-pix_fmt" "yuv420p"
                            "-preset" "medium"
                            "-crf" "18"
                            "-c:a" "aac"
                            "-b:a" "320k"
                            "-shortest"
                            fin]))
             out (BufferedOutputStream. (.getOutputStream proc) (* 4 1024 1024))
             err (future (slurp (.getErrorStream proc)))]

         (try
           (dotimes [i total]
             (let [g (aget scales i)
                   img (render-frame bg-img pic g w h)]
               (.write out (image->rgb24 img))
               (swap! frames-done! inc)
               (when (zero? (mod i 50))
                 (log (format "%.2f%% (%d/%d)"
                              (* 100.0 (/ @frames-done! total)) i total)))))
           (.flush out)
           (.close out)
           (catch Exception e
             (.close out)
             (throw e)))

         (.waitFor proc)
         (let [e @err]
           (when (seq e) (log "ffmpeg stderr:" e)))
         (.waitFor proc)
         (log "Done:" fin)))

     (io/delete-file background)
     (io/delete-file cover-path)
     (log fin)
     (shutdown-agents))))