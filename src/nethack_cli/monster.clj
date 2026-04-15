(ns nethack-cli.monster
  "Monster definitions, spawning, and AI for combat system.")

;; Monster type definitions
;; Each monster has: :type, :symbol, :name, :hp, :max-hp, :damage (min-max), :speed
(def monster-types
  {:rat    {:symbol \r :name "rat"    :max-hp 3  :damage [1 2]}
   :snake  {:symbol \s :name "snake"  :max-hp 5  :damage [1 3]}
   :goblin {:symbol \g :name "goblin" :max-hp 8  :damage [2 4]}})

(defn create-monster
  "Create a monster of the given type at position [x y]."
  [monster-type x y]
  (let [template (get monster-types monster-type)]
    (when template
      (merge template
             {:type monster-type
              :x x
              :y y
              :hp (:max-hp template)
              :id (random-uuid)}))))

(defn monster-at
  "Find a monster at position [x y] in the monsters collection."
  [monsters x y]
  (first (filter #(and (= (:x %) x) (= (:y %) y)) monsters)))

(defn alive?
  "Check if a monster is still alive (hp > 0)."
  [monster]
  (and monster (> (:hp monster) 0)))

(defn remove-dead
  "Remove all dead monsters from the collection."
  [monsters]
  (filterv alive? monsters))

(defn roll-damage
  "Roll damage between min and max (inclusive)."
  [[min-dmg max-dmg]]
  (+ min-dmg (rand-int (inc (- max-dmg min-dmg)))))

(defn distance
  "Calculate Manhattan distance between two points."
  [x1 y1 x2 y2]
  (+ (abs (- x2 x1)) (abs (- y2 y1))))

(defn adjacent?
  "Check if two positions are adjacent (including diagonals)."
  [x1 y1 x2 y2]
  (and (<= (abs (- x2 x1)) 1)
       (<= (abs (- y2 y1)) 1)
       (not (and (= x1 x2) (= y1 y2)))))

;; Monster AI
(def ai-range 5) ;; Monsters move toward player if within this range

(defn in-ai-range?
  "Check if monster is within AI activation range of player."
  [monster player-x player-y]
  (<= (distance (:x monster) (:y monster) player-x player-y) ai-range))

(defn move-toward
  "Calculate the next position for monster moving toward target.
   Returns [new-x new-y] or nil if no valid move."
  [monster target-x target-y walkable?]
  (let [{:keys [x y]} monster
        dx (compare target-x x)
        dy (compare target-y y)
        ;; Try diagonal move first, then cardinal directions
        candidates (cond
                     (and (not= dx 0) (not= dy 0))
                     [[(+ x dx) (+ y dy)]    ;; diagonal
                      [(+ x dx) y]            ;; horizontal
                      [x (+ y dy)]]           ;; vertical

                     (not= dx 0)
                     [[(+ x dx) y]]

                     (not= dy 0)
                     [[x (+ y dy)]]

                     :else nil)]
    (first (filter (fn [[nx ny]] (walkable? nx ny)) candidates))))

(defn monster-ai-turn
  "Process AI for a single monster. Returns updated monster and optional attack.
   walkable? is a predicate (fn [x y]) -> bool for checking valid positions.
   Returns {:monster updated-monster :attack {:damage n} or nil}"
  [monster player-x player-y walkable? occupied?]
  (cond
    ;; If adjacent to player, attack
    (adjacent? (:x monster) (:y monster) player-x player-y)
    {:monster monster
     :attack {:damage (roll-damage (:damage monster))
              :attacker-name (:name monster)}}

    ;; If in range, move toward player
    (in-ai-range? monster player-x player-y)
    (if-let [[new-x new-y] (move-toward monster player-x player-y
                                        (fn [nx ny]
                                          (and (walkable? nx ny)
                                               (not (occupied? nx ny))
                                               (not (and (= nx player-x) (= ny player-y))))))]
      {:monster (assoc monster :x new-x :y new-y)
       :attack nil}
      {:monster monster :attack nil})

    ;; Otherwise, idle
    :else
    {:monster monster :attack nil}))

(defn process-monster-turns
  "Process all monster AI turns. Returns {:monsters [updated-monsters] :attacks [attack-info]}
   walkable? checks if terrain allows movement.
   player-x, player-y is the player position."
  [monsters player-x player-y walkable?]
  (loop [remaining monsters
         processed []
         attacks []]
    (if (empty? remaining)
      {:monsters processed :attacks attacks}
      (let [monster (first remaining)
            ;; Create occupied? predicate for remaining monsters and already processed ones
            occupied? (fn [x y]
                        (or (some #(and (= (:x %) x) (= (:y %) y)) (rest remaining))
                            (some #(and (= (:x %) x) (= (:y %) y)) processed)))
            {:keys [monster attack]} (monster-ai-turn monster player-x player-y walkable? occupied?)]
        (recur (rest remaining)
               (conj processed monster)
               (if attack (conj attacks attack) attacks))))))

;; Monster spawning
(defn random-monster-type
  "Pick a random monster type."
  []
  (rand-nth (keys monster-types)))

(defn spawn-monsters
  "Spawn 3-5 monsters on the floor. 
   valid-positions is a seq of [x y] floor tile positions.
   avoid-positions is a set of positions to avoid (e.g., player start, stairs)."
  [valid-positions avoid-positions]
  (let [count (+ 3 (rand-int 3)) ;; 3, 4, or 5 monsters
        available (remove #(contains? avoid-positions %) valid-positions)
        positions (take count (shuffle available))]
    (mapv (fn [[x y]]
            (create-monster (random-monster-type) x y))
          positions)))

(defn spawn-monsters-in-rooms
  "Spawn monsters preferring room positions.
   room-positions is a seq of [x y] positions inside rooms.
   Returns a vector of monsters."
  [room-positions avoid-positions]
  (spawn-monsters room-positions avoid-positions))
