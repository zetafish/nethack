(ns nethack-cli.core
  (:require [lanterna.screen :as s]
            [nethack-cli.dungeon :as dungeon])
  (:gen-class))

(defn render-floor!
  "Render dungeon floor tiles to lanterna screen."
  [screen {:keys [tiles]}]
  (doseq [y (range (count tiles))
          x (range (count (nth tiles y)))]
    (s/put-string screen x y (str (dungeon/get-tile tiles x y)))))

(defn welcome-screen!
  "Show welcome screen, wait for keypress. Returns false if user quit."
  [scr]
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
    (not= (s/get-key-blocking scr) \q)))

(defn -main [& _args]
  (let [scr (s/get-screen :unix)]
    (s/start scr)
    (try
      (when (welcome-screen! scr)
        (s/clear scr)
        (let [floor (dungeon/generate-floor)]
          (render-floor! scr floor)
          (s/put-string scr 0 (+ (:height floor) 1)
                        "Dungeon generated! Press 'q' to quit.")
          (s/redraw scr)
          (loop []
            (let [key (s/get-key-blocking scr)]
              (when-not (= key \q)
                (recur))))))
      (finally
        (s/stop scr)))))
