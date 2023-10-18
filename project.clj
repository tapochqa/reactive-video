(defproject reactive-video "0.2.1"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  
  :dependencies [[org.clojure/clojure       "1.11.1"]
                 [org.clojars.tapochqa/lufs "0.6.5"]
                 [fivetonine/collage        "0.3.0"]
                 [image-resizer             "0.1.10"]
                 [me.raynes/conch           "0.8.0"]
                 [tvirolai/blurhash         "0.0.8"]
                 [link.lmnd/yandex-music    "0.1.2"]
                 [com.github.igrishaev/virtuoso "0.1.0"]

]
  
  :repl-options {:init-ns reactive-video.core}
  :uberjar-name "reactive-video.jar"
  :profiles { :uberjar {
                    :aot :all}}
  :main reactive-video.core)