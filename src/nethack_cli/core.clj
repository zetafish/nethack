(ns nethack-cli.core
  (:require [lanterna.screen :as s]
            [nethack-cli.dungeon :as dungeon]))

(defn render-floor!
  "Render dungeon floor tiles to lanterna screen."
  [screen {:keys [tiles]}]
  (doseq [y (range (count tiles))
          x (range (count (nth tiles y)))]
    (s/put-string screen x y (str (dungeon/get-tile tiles x y)))))

(defn -main [& args]
  (let [screen (s/get-screen :unix)]
    (s/start screen)
    (try
      (let [floor (dungeon/generate-floor)]
        (render-floor! screen floor)
        (s/put-string screen 0 (+ (:height floor) 1)
                      "Dungeon generated! Press 'q' to quit.")
        (s/redraw screen)
        (loop []
          (let [key (s/get-key-blocking screen)]
            (when-not (= key \q)
              (recur)))))
      (finally
        (s/stop screen)))))
