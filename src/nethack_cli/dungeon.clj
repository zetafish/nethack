(ns nethack-cli.dungeon)

(def min-width 40)
(def min-height 20)
(def default-width 60)
(def default-height 22)

(def min-room-size 4)
(def max-room-size 8)
(def max-room-attempts 50)
(def target-room-count 6)

(defn make-grid
  "Create a width x height grid filled with wall characters."
  [width height]
  (vec (repeat height (vec (repeat width \#)))))

(defn get-tile [grid x y]
  (get-in grid [y x]))

(defn set-tile [grid x y tile]
  (assoc-in grid [y x] tile))

(defn make-room
  "Generate a random room {:x :y :w :h} within grid bounds."
  [width height rng]
  (let [w (+ min-room-size (rand-int (- max-room-size min-room-size)))
        h (+ min-room-size (rand-int (- max-room-size min-room-size)))
        x (+ 1 (rand-int (- width w 2)))
        y (+ 1 (rand-int (- height h 2)))]
    {:x x :y y :w w :h h}))

(defn rooms-overlap?
  "Check if two rooms overlap (with 1-tile padding)."
  [r1 r2]
  (not (or (>= (:x r1) (+ (:x r2) (:w r2) 1))
           (>= (:x r2) (+ (:x r1) (:w r1) 1))
           (>= (:y r1) (+ (:y r2) (:h r2) 1))
           (>= (:y r2) (+ (:y r1) (:h r1) 1)))))

(defn room-center [room]
  [(+ (:x room) (quot (:w room) 2))
   (+ (:y room) (quot (:h room) 2))])

(defn carve-room
  "Carve a room into the grid by setting floor tiles."
  [grid room]
  (reduce (fn [g [x y]]
            (set-tile g x y \.))
          grid
          (for [dy (range (:h room))
                dx (range (:w room))]
            [(+ (:x room) dx) (+ (:y room) dy)])))

(defn carve-h-corridor
  "Carve a horizontal corridor from x1 to x2 at row y."
  [grid x1 x2 y]
  (let [start (min x1 x2)
        end (max x1 x2)]
    (reduce (fn [g x] (set-tile g x y \.))
            grid
            (range start (inc end)))))

(defn carve-v-corridor
  "Carve a vertical corridor from y1 to y2 at column x."
  [grid x y1 y2]
  (let [start (min y1 y2)
        end (max y1 y2)]
    (reduce (fn [g y] (set-tile g x y \.))
            grid
            (range start (inc end)))))

(defn connect-rooms
  "Connect two rooms with an L-shaped corridor."
  [grid room1 room2]
  (let [[x1 y1] (room-center room1)
        [x2 y2] (room-center room2)]
    (if (< (rand) 0.5)
      (-> grid
          (carve-h-corridor x1 x2 y1)
          (carve-v-corridor x2 y1 y2))
      (-> grid
          (carve-v-corridor x1 y1 y2)
          (carve-h-corridor x1 x2 y2)))))

(defn generate-rooms
  "Generate non-overlapping rooms."
  [width height]
  (loop [rooms []
         attempts 0]
    (if (or (>= (count rooms) target-room-count)
            (>= attempts max-room-attempts))
      rooms
      (let [room (make-room width height nil)]
        (if (some #(rooms-overlap? room %) rooms)
          (recur rooms (inc attempts))
          (recur (conj rooms room) (inc attempts)))))))

(defn place-staircase
  "Place staircase down in a random room (not the first room)."
  [grid rooms]
  (let [room (rand-nth (rest rooms))
        [x y] (room-center room)]
    (set-tile grid x y \>)))

(defn floor-tiles
  "Return all [x y] positions that are floor tiles."
  [grid]
  (let [height (count grid)
        width (count (first grid))]
    (for [y (range height)
          x (range width)
          :when (= (get-tile grid x y) \.)]
      [x y])))

(defn neighbors [[x y]]
  [[(dec x) y] [(inc x) y] [x (dec y)] [x (inc y)]])

(defn flood-fill
  "BFS flood fill from start, returning set of reachable floor/staircase tiles."
  [grid start]
  (let [height (count grid)
        width (count (first grid))
        passable? #{\. \>}]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (if (empty? queue)
        visited
        (let [pos (peek queue)
              queue (pop queue)
              nbrs (->> (neighbors pos)
                        (filter (fn [[x y]]
                                  (and (>= x 0) (< x width)
                                       (>= y 0) (< y height)
                                       (not (visited [x y]))
                                       (passable? (get-tile grid x y))))))]
          (recur (into queue nbrs)
                 (into visited nbrs)))))))

(defn connected?
  "Check if all floor tiles are reachable from any floor tile."
  [grid]
  (let [floors (floor-tiles grid)]
    (when (seq floors)
      (let [reachable (flood-fill grid (first floors))]
        (every? reachable floors)))))

(defn generate-floor
  "Generate a complete dungeon floor. Returns {:tiles grid :rooms rooms}."
  ([] (generate-floor default-width default-height))
  ([width height]
   (loop [attempt 0]
     (let [rooms (generate-rooms width height)
           grid (reduce carve-room (make-grid width height) rooms)
           grid (reduce (fn [g [r1 r2]] (connect-rooms g r1 r2))
                        grid
                        (partition 2 1 rooms))
           grid (place-staircase grid rooms)]
       (if (or (connected? grid) (>= attempt 10))
         {:tiles grid
          :rooms rooms
          :width width
          :height height}
         (recur (inc attempt)))))))

(defn floor->str
  "Render floor tiles to a string for debugging."
  [{:keys [tiles]}]
  (->> tiles
       (map #(apply str %))
       (clojure.string/join "\n")))
