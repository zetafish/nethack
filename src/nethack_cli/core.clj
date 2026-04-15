(ns nethack-cli.core
  (:require [lanterna.screen :as s]
            [nethack-cli.dungeon :as dungeon])
  (:gen-class))

(defn initial-game-state []
  (let [floor (dungeon/generate-floor 1)
        [px py] (dungeon/player-start-pos floor)]
    {:player {:x px :y py :hp 20}
     :floors {1 floor}
     :current-floor 1
     :messages ["Welcome to NetHack CLI! Navigate with arrow keys/hjkl. Use > and < on stairs."]
     :game-over? false
     :won? false}))

(defn current-floor [state]
  (get-in state [:floors (:current-floor state)]))

(defn ensure-floor [state floor-num]
  (if (get-in state [:floors floor-num])
    state
    (assoc-in state [:floors floor-num] (dungeon/generate-floor floor-num))))

(defn try-move [state dx dy]
  (let [px (+ (get-in state [:player :x]) dx)
        py (+ (get-in state [:player :y]) dy)
        floor (current-floor state)]
    (if (dungeon/walkable? floor px py)
      (-> state
          (assoc-in [:player :x] px)
          (assoc-in [:player :y] py)
          (assoc :messages []))
      state)))

(defn descend [state]
  (let [floor (current-floor state)
        px (get-in state [:player :x])
        py (get-in state [:player :y])
        tile (dungeon/floor-tile-at floor px py)
        cur (:current-floor state)]
    (if (and (= tile \>) (< cur dungeon/total-floors))
      (let [next-floor (inc cur)
            state (ensure-floor state next-floor)
            new-floor (get-in state [:floors next-floor])
            [sx sy] (or (:up-stairs new-floor)
                        (dungeon/player-start-pos new-floor))]
        (-> state
            (assoc :current-floor next-floor)
            (assoc-in [:player :x] sx)
            (assoc-in [:player :y] sy)
            (assoc :messages [(str "You descend to floor " next-floor ".")])))
      (assoc state :messages ["You can't go down here."]))))

(defn ascend [state]
  (let [floor (current-floor state)
        px (get-in state [:player :x])
        py (get-in state [:player :y])
        tile (dungeon/floor-tile-at floor px py)
        cur (:current-floor state)]
    (if (and (= tile \<) (> cur 1))
      (let [prev-floor (dec cur)
            state (ensure-floor state prev-floor)
            old-floor (get-in state [:floors prev-floor])
            [sx sy] (or (:down-stairs old-floor)
                        (dungeon/player-start-pos old-floor))]
        (-> state
            (assoc :current-floor prev-floor)
            (assoc-in [:player :x] sx)
            (assoc-in [:player :y] sy)
            (assoc :messages [(str "You ascend to floor " prev-floor ".")])))
      (assoc state :messages ["You can't go up here."]))))

(defn check-amulet [state]
  (let [floor (current-floor state)
        px (get-in state [:player :x])
        py (get-in state [:player :y])]
    (if (and (= (:current-floor state) dungeon/total-floors)
             (= (:amulet-pos floor) [px py]))
      (-> state
          (assoc :won? true)
          (assoc :game-over? true)
          (assoc :messages ["You found the Amulet of Yendor! You win!"]))
      state)))

(defn handle-input [state key]
  (case key
    (\h :left)  (-> (try-move state -1 0) check-amulet)
    (\l :right) (-> (try-move state 1 0) check-amulet)
    (\k :up)    (-> (try-move state 0 -1) check-amulet)
    (\j :down)  (-> (try-move state 0 1) check-amulet)
    \> (descend state)
    \< (ascend state)
    state))

(defn render-game [scr state]
  (s/clear scr)
  (let [floor (current-floor state)
        tiles (:tiles floor)
        px (get-in state [:player :x])
        py (get-in state [:player :y])]
    ;; Draw tiles
    (doseq [y (range (count tiles))
            x (range (count (first tiles)))]
      (s/put-string scr x (inc y) (str (get-in tiles [y x]))))
    ;; Draw player
    (s/put-string scr px (inc py) "@")
    ;; HUD line at top
    (let [hud (format "Floor: %d/%d  HP: %d  Pos: [%d,%d]"
                      (:current-floor state) dungeon/total-floors
                      (get-in state [:player :hp]) px py)]
      (s/put-string scr 0 0 hud))
    ;; Messages at bottom
    (when-let [msg (first (:messages state))]
      (s/put-string scr 0 (+ 2 (count tiles)) msg)))
  (s/redraw scr))

(defn render-end-screen [scr state]
  (s/clear scr)
  (let [[cols rows] (s/get-size scr)
        msg (if (:won? state)
              "*** YOU WIN! You found the Amulet of Yendor! ***"
              "*** GAME OVER ***")
        detail (format "Final floor: %d" (:current-floor state))
        mx (max 0 (quot (- cols (count msg)) 2))
        my (quot rows 2)]
    (s/put-string scr mx my msg)
    (s/put-string scr mx (+ my 2) detail)
    (s/put-string scr mx (+ my 4) "Press any key to exit."))
  (s/redraw scr)
  (s/get-key-blocking scr))

(defn -main [& _args]
  (let [scr (s/get-screen :unix)]
    (s/start scr)
    (try
      (loop [state (initial-game-state)]
        (if (:game-over? state)
          (render-end-screen scr state)
          (do
            (render-game scr state)
            (let [key (s/get-key-blocking scr)]
              (if (= key \q)
                nil
                (recur (handle-input state key)))))))
      (finally
        (s/stop scr)))))
