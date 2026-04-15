(ns nethack-cli.dungeon)

(def floor-width 60)
(def floor-height 20)
(def total-floors 5)

(defn make-empty-grid [w h fill]
  (vec (repeat h (vec (repeat w fill)))))

(defn set-tile [grid x y tile]
  (if (and (>= x 0) (< x (count (first grid)))
           (>= y 0) (< y (count grid)))
    (assoc-in grid [y x] tile)
    grid))

(defn get-tile [grid x y]
  (get-in grid [y x]))

(defn carve-room [grid {:keys [x y w h]}]
  (reduce (fn [g [cx cy]]
            (set-tile g cx cy \.))
          grid
          (for [cy (range y (+ y h))
                cx (range x (+ x w))]
            [cx cy])))

(defn carve-corridor [grid [x1 y1] [x2 y2]]
  (let [g1 (reduce (fn [g x] (set-tile g x y1 \.))
                   grid
                   (range (min x1 x2) (inc (max x1 x2))))
        g2 (reduce (fn [g y] (set-tile g x2 y \.))
                   g1
                   (range (min y1 y2) (inc (max y1 y2))))]
    g2))

(defn random-room [rng]
  (let [w (+ 4 (rand-int 6))
        h (+ 3 (rand-int 4))
        x (+ 1 (rand-int (- floor-width w 2)))
        y (+ 1 (rand-int (- floor-height h 2)))]
    {:x x :y y :w w :h h}))

(defn room-center [{:keys [x y w h]}]
  [(+ x (quot w 2)) (+ y (quot h 2))])

(defn rooms-overlap? [r1 r2]
  (not (or (>= (:x r1) (+ (:x r2) (:w r2) 1))
           (>= (:x r2) (+ (:x r1) (:w r1) 1))
           (>= (:y r1) (+ (:y r2) (:h r2) 1))
           (>= (:y r2) (+ (:y r1) (:h r1) 1)))))

(defn generate-rooms []
  (loop [rooms []
         attempts 0]
    (if (or (>= (count rooms) 6) (>= attempts 50))
      rooms
      (let [room (random-room nil)]
        (if (some #(rooms-overlap? room %) rooms)
          (recur rooms (inc attempts))
          (recur (conj rooms room) (inc attempts)))))))

(defn generate-floor
  "Generate a dungeon floor. floor-num is 1-based."
  [floor-num]
  (let [rooms (generate-rooms)
        grid (make-empty-grid floor-width floor-height \#)
        grid (reduce carve-room grid rooms)
        centers (map room-center rooms)
        grid (reduce (fn [g [c1 c2]] (carve-corridor g c1 c2))
                     grid
                     (partition 2 1 centers))
        ;; Place stairs
        first-center (room-center (first rooms))
        last-center (room-center (last rooms))
        ;; Up stairs on all floors except floor 1
        grid (if (> floor-num 1)
               (set-tile grid (first first-center) (second first-center) \<)
               grid)
        ;; Down stairs on all floors except floor 5
        grid (if (< floor-num total-floors)
               (set-tile grid (first last-center) (second last-center) \>)
               grid)
        ;; Amulet on floor 5
        grid (if (= floor-num total-floors)
               (set-tile grid (first last-center) (second last-center) \")
               grid)]
    {:tiles grid
     :rooms rooms
     :floor-num floor-num
     :monsters []
     :up-stairs (when (> floor-num 1) first-center)
     :down-stairs (when (< floor-num total-floors) last-center)
     :amulet-pos (when (= floor-num total-floors) last-center)}))

(defn floor-tile-at [floor x y]
  (get-tile (:tiles floor) x y))

(defn walkable? [floor x y]
  (let [tile (floor-tile-at floor x y)]
    (and tile (not= tile \#))))

(defn player-start-pos [floor]
  "Return starting position for player on this floor (center of first room)."
  (room-center (first (:rooms floor))))
