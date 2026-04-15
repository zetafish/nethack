(ns nethack-cli.victory
  "Win condition logic for NetHack CLI.
   
   Handles the Amulet of Yendor placement, pickup, victory screen,
   and final score calculation.
   
   The Amulet is placed on floor 5 (the bottom floor) and picking it up
   triggers the victory condition."
  (:require [lanterna.screen :as s]))

;; Constants
(def amulet-char \")  ; The Amulet of Yendor is represented by "
(def amulet-floor 5) ; Amulet appears on floor 5 (bottom floor)
(def total-floors 5) ; Total number of dungeon floors

;; ============================================================================
;; Amulet Placement
;; ============================================================================

(defn place-amulet
  "Places the Amulet of Yendor on a random floor tile of the given floor.
   Returns the floor map with :amulet-pos set to [x y], or nil if already picked up.
   
   Arguments:
   - floor-map: The floor data map containing :tiles (2D vector of chars)
   - floor-number: Current floor number (1-indexed)
   
   Returns:
   - Updated floor-map with :amulet-pos key if this is floor 5"
  [floor-map floor-number]
  (if (= floor-number amulet-floor)
    (let [tiles (:tiles floor-map)
          height (count tiles)
          width (count (first tiles))
          ;; Find all floor tiles (.) that could hold the amulet
          floor-positions (for [y (range height)
                                x (range width)
                                :when (= \. (get-in tiles [y x]))]
                            [x y])
          ;; Pick a random position
          amulet-pos (when (seq floor-positions)
                       (rand-nth floor-positions))]
      (if amulet-pos
        (assoc floor-map :amulet-pos amulet-pos)
        floor-map))
    floor-map))

(defn has-amulet?
  "Returns true if the floor has an amulet placed on it."
  [floor-map]
  (boolean (:amulet-pos floor-map)))

(defn amulet-at?
  "Returns true if the amulet is at the given position."
  [floor-map x y]
  (= (:amulet-pos floor-map) [x y]))

;; ============================================================================
;; Amulet Pickup
;; ============================================================================

(defn pickup-amulet
  "Attempts to pick up the amulet if the player is standing on it.
   
   Arguments:
   - game-state: The current game state map
   
   Returns:
   - Updated game-state with :has-amulet true and amulet removed from floor,
     or unchanged game-state if no amulet at player position"
  [game-state]
  (let [player (:player game-state)
        px (:x player)
        py (:y player)
        current-floor-idx (:current-floor game-state)
        floors (:floors game-state)
        floor-map (get floors current-floor-idx)]
    (if (amulet-at? floor-map px py)
      (-> game-state
          (assoc :has-amulet true)
          (assoc :victory true)
          (assoc-in [:floors current-floor-idx :amulet-pos] nil)
          (update :messages conj "You pick up the Amulet of Yendor! You have won!"))
      game-state)))

(defn check-victory
  "Checks if the player has achieved victory (picked up the amulet).
   This should be called after each move.
   
   Returns updated game-state with victory flag if applicable."
  [game-state]
  (if (:has-amulet game-state)
    (assoc game-state :victory true)
    game-state))

;; ============================================================================
;; Score Calculation
;; ============================================================================

(defn calculate-score
  "Calculates the final score based on game progress.
   
   Score components:
   - 100 points per floor explored
   - 50 points per monster killed
   - 1000 bonus points for retrieving the Amulet
   
   Arguments:
   - game-state: The final game state
   
   Returns:
   - A map with :total, :floors-explored, :monsters-killed, :amulet-bonus"
  [game-state]
  (let [floors-explored (count (filter :visited (:floors game-state)))
        ;; Count monsters killed (tracked in :kills or calculate from initial - remaining)
        monsters-killed (get game-state :monsters-killed 0)
        amulet-bonus (if (:has-amulet game-state) 1000 0)
        floor-points (* 100 floors-explored)
        monster-points (* 50 monsters-killed)
        total (+ floor-points monster-points amulet-bonus)]
    {:total total
     :floors-explored floors-explored
     :monsters-killed monsters-killed
     :floor-points floor-points
     :monster-points monster-points
     :amulet-bonus amulet-bonus}))

;; ============================================================================
;; Victory Screen
;; ============================================================================

(defn draw-victory-screen
  "Draws the victory screen with congratulations message and final score.
   
   Arguments:
   - screen: The lanterna screen object
   - game-state: The final game state"
  [screen game-state]
  (let [[cols rows] (s/get-size screen)
        score (calculate-score game-state)

        ;; Victory messages
        title "*** VICTORY! ***"
        msg1 "Congratulations! You have retrieved the Amulet of Yendor!"
        msg2 "You have escaped the dungeon and won the game!"

        ;; Score lines
        score-header "=== FINAL SCORE ==="
        score-floors (str "Floors Explored: " (:floors-explored score)
                          " (" (:floor-points score) " points)")
        score-monsters (str "Monsters Slain: " (:monsters-killed score)
                            " (" (:monster-points score) " points)")
        score-amulet (str "Amulet Bonus: " (:amulet-bonus score) " points")
        score-total (str "TOTAL SCORE: " (:total score))

        prompt "Press any key to exit..."

        ;; Center positions
        center-x (fn [text] (max 0 (quot (- cols (count text)) 2)))
        center-y (quot rows 2)]

    ;; Clear screen
    (s/clear screen)

    ;; Draw title (centered, near top)
    (s/put-string screen (center-x title) (- center-y 6) title)

    ;; Draw victory messages
    (s/put-string screen (center-x msg1) (- center-y 4) msg1)
    (s/put-string screen (center-x msg2) (- center-y 3) msg2)

    ;; Draw score section
    (s/put-string screen (center-x score-header) (- center-y 1) score-header)
    (s/put-string screen (center-x score-floors) center-y score-floors)
    (s/put-string screen (center-x score-monsters) (+ center-y 1) score-monsters)
    (s/put-string screen (center-x score-amulet) (+ center-y 2) score-amulet)
    (s/put-string screen (center-x score-total) (+ center-y 4) score-total)

    ;; Draw exit prompt
    (s/put-string screen (center-x prompt) (+ center-y 7) prompt)

    (s/redraw screen)))

(defn show-victory-screen
  "Shows the victory screen and waits for a keypress to exit.
   
   Arguments:
   - screen: The lanterna screen object
   - game-state: The final game state
   
   Returns:
   - nil (exits after keypress)"
  [screen game-state]
  (draw-victory-screen screen game-state)
  ;; Wait for any keypress
  (s/get-key-blocking screen)
  nil)

;; ============================================================================
;; Game Over Screen (for non-victory endings)
;; ============================================================================

(defn draw-game-over-screen
  "Draws the game over screen (player died).
   
   Arguments:
   - screen: The lanterna screen object
   - game-state: The final game state"
  [screen game-state]
  (let [[cols rows] (s/get-size screen)
        score (calculate-score game-state)

        title "*** GAME OVER ***"
        msg1 "You have perished in the dungeon..."
        msg2 (str "You died on floor " (inc (:current-floor game-state 0)))

        score-header "=== FINAL SCORE ==="
        score-floors (str "Floors Explored: " (:floors-explored score))
        score-monsters (str "Monsters Slain: " (:monsters-killed score))
        score-total (str "TOTAL SCORE: " (:total score))

        prompt "Press any key to exit..."

        center-x (fn [text] (max 0 (quot (- cols (count text)) 2)))
        center-y (quot rows 2)]

    (s/clear screen)

    (s/put-string screen (center-x title) (- center-y 4) title)
    (s/put-string screen (center-x msg1) (- center-y 2) msg1)
    (s/put-string screen (center-x msg2) (- center-y 1) msg2)

    (s/put-string screen (center-x score-header) (+ center-y 1) score-header)
    (s/put-string screen (center-x score-floors) (+ center-y 2) score-floors)
    (s/put-string screen (center-x score-monsters) (+ center-y 3) score-monsters)
    (s/put-string screen (center-x score-total) (+ center-y 5) score-total)

    (s/put-string screen (center-x prompt) (+ center-y 8) prompt)

    (s/redraw screen)))

(defn show-game-over-screen
  "Shows the game over screen and waits for a keypress to exit.
   
   Arguments:
   - screen: The lanterna screen object
   - game-state: The final game state
   
   Returns:
   - nil (exits after keypress)"
  [screen game-state]
  (draw-game-over-screen screen game-state)
  (s/get-key-blocking screen)
  nil)

;; ============================================================================
;; End Game Handler
;; ============================================================================

(defn handle-end-game
  "Handles the end of the game, showing either victory or game over screen.
   
   Arguments:
   - screen: The lanterna screen object
   - game-state: The final game state
   
   Returns:
   - nil"
  [screen game-state]
  (cond
    (:victory game-state) (show-victory-screen screen game-state)
    (:game-over game-state) (show-game-over-screen screen game-state)
    :else nil))

;; ============================================================================
;; Rendering Helper
;; ============================================================================

(defn render-amulet
  "Renders the amulet on the screen if it exists at the given floor.
   Should be called as part of floor rendering.
   
   Arguments:
   - screen: The lanterna screen object
   - floor-map: The current floor data
   - offset-x: X offset for rendering (for viewport)
   - offset-y: Y offset for rendering (for viewport)"
  [screen floor-map offset-x offset-y]
  (when-let [[ax ay] (:amulet-pos floor-map)]
    (s/put-string screen (+ ax offset-x) (+ ay offset-y) (str amulet-char))))

;; ============================================================================
;; Initial State Helper
;; ============================================================================

(defn init-victory-state
  "Returns the initial victory-related state to merge into game state."
  []
  {:has-amulet false
   :victory false
   :monsters-killed 0})
