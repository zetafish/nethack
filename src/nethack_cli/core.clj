(ns nethack-cli.core
  (:require [lanterna.screen :as s]
            [nethack-cli.dungeon :as dungeon])
  (:gen-class))

(defn key->direction [key]
  (case key
    (\h :left)  [-1 0]
    (\j :down)  [0 1]
    (\k :up)    [0 -1]
    (\l :right) [1 0]
    nil))

(defn move-player [state dx dy]
  (let [{:keys [player floor]} state
        nx (+ (:x player) dx)
        ny (+ (:y player) dy)]
    (if (dungeon/walkable? (:tiles floor) nx ny)
      (assoc state :player (assoc player :x nx :y ny))
      state)))

(defn render! [scr state]
  (s/clear scr)
  (let [{:keys [player floor]} state
        tiles (:tiles floor)]
    (doseq [y (range (count tiles))
            x (range (count (nth tiles y)))]
      (s/put-string scr x y (str (dungeon/get-tile tiles x y))))
    (s/put-string scr (:x player) (:y player) "@"))
  (s/redraw scr))

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

(defn game-loop! [scr]
  (let [floor (dungeon/generate-floor)
        [px py] (dungeon/player-start floor)
        init-state {:player {:x px :y py :hp 20}
                    :floor floor}]
    (loop [state init-state]
      (render! scr state)
      (let [key (s/get-key-blocking scr)]
        (if (= key \q)
          nil
          (if-let [[dx dy] (key->direction key)]
            (recur (move-player state dx dy))
            (recur state)))))))

(defn -main [& _args]
  (let [scr (s/get-screen :unix)]
    (s/start scr)
    (try
      (when (welcome-screen! scr)
        (game-loop! scr))
      (finally
        (s/stop scr)))))
