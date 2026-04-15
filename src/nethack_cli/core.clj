(ns nethack-cli.core
  (:require [lanterna.screen :as s])
  (:gen-class))

(defn -main [& _args]
  (let [scr (s/get-screen :unix)]
    (s/start scr)
    (try
      (let [[cols rows] (s/get-size scr)
            title "Welcome to NetHack CLI"
            subtitle "Press any key to begin, or 'q' to quit."
            title-x (max 0 (quot (- cols (count title)) 2))
            title-y (max 0 (quot rows 2))
            sub-x (max 0 (quot (- cols (count subtitle)) 2))
            sub-y (inc title-y)]
        (s/put-string scr title-x title-y title)
        (s/put-string scr sub-x sub-y subtitle)
        (s/redraw scr)
        (loop []
          (let [key (s/get-key-blocking scr)]
            (when-not (= key \q)
              (recur)))))
      (finally
        (s/stop scr)))))
