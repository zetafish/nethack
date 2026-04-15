(ns nethack-cli.hud
  "HUD (Heads-Up Display) and message system for NetHack CLI.
   
   Provides functions to render:
   - Status bar with floor number, HP, and player position
   - Message line for action feedback
   - Game over screen with final stats
   - Victory screen when amulet is collected"
  (:require [lanterna.screen :as scr]))

;; ============================================================================
;; Constants
;; ============================================================================

(def ^:const status-bar-row 0)
(def ^:const message-row 1)
(def ^:const dungeon-start-row 2)

(def ^:const max-messages 5)

;; ============================================================================
;; Message Management
;; ============================================================================

(defn add-message
  "Add a message to the game state's message queue.
   Keeps only the last `max-messages` messages."
  [game-state message]
  (update game-state :messages
          (fn [msgs]
            (take max-messages (cons message (or msgs []))))))

(defn clear-messages
  "Clear all messages from the game state."
  [game-state]
  (assoc game-state :messages []))

(defn get-last-message
  "Get the most recent message from the game state."
  [game-state]
  (first (:messages game-state)))

;; ============================================================================
;; Status Bar Rendering
;; ============================================================================

(defn format-status-bar
  "Format the status bar string with floor, HP, and position.
   
   Args:
     game-state - The current game state map containing:
       :current-floor - Current floor number (1-5)
       :player - Player map with :x, :y, :hp, :max-hp
   
   Returns:
     A formatted status string like 'Floor:3 | HP:15/20 | Pos:(12,8)'"
  [{:keys [current-floor player]}]
  (let [{:keys [x y hp max-hp]} player
        floor-num (or current-floor 1)
        current-hp (or hp 20)
        maximum-hp (or max-hp 20)]
    (format "Floor:%d | HP:%d/%d | Pos:(%d,%d)"
            floor-num current-hp maximum-hp (or x 0) (or y 0))))

(defn render-status-bar
  "Render the status bar to the screen.
   
   Args:
     screen - The lanterna screen object
     game-state - The current game state
     width - Screen width for padding/clearing"
  [screen game-state width]
  (let [status-text (format-status-bar game-state)
        padded-text (format (str "%-" width "s") status-text)]
    (scr/put-string screen 0 status-bar-row padded-text
                    {:fg :white :bg :blue})))

;; ============================================================================
;; Message Line Rendering
;; ============================================================================

(defn render-message-line
  "Render the message line showing the last action.
   
   Args:
     screen - The lanterna screen object
     game-state - The current game state (contains :messages)
     width - Screen width for padding/clearing"
  [screen game-state width]
  (let [message (or (get-last-message game-state) "")
        padded-message (format (str "%-" width "s") message)]
    (scr/put-string screen 0 message-row padded-message
                    {:fg :yellow :bg :black})))

;; ============================================================================
;; Game Over Screen
;; ============================================================================

(defn format-game-over-stats
  "Format the final game statistics for display.
   
   Args:
     game-state - The final game state containing:
       :current-floor - Final floor number
       :stats - Optional stats map with :monsters-killed, :turns
   
   Returns:
     A vector of strings to display."
  [{:keys [current-floor stats]}]
  (let [floor (or current-floor 1)
        kills (get stats :monsters-killed 0)
        turns (get stats :turns 0)]
    [""
     "=========================================="
     "               GAME OVER"
     "=========================================="
     ""
     (format "        You died on floor %d" floor)
     ""
     (format "        Monsters slain: %d" kills)
     (format "        Turns survived: %d" turns)
     ""
     "=========================================="
     ""
     "        Press any key to exit..."]))

(defn render-game-over
  "Render the game over screen.
   
   Args:
     screen - The lanterna screen object
     game-state - The final game state
     width - Screen width
     height - Screen height"
  [screen game-state width height]
  (scr/clear screen)
  (let [lines (format-game-over-stats game-state)
        start-y (max 0 (quot (- height (count lines)) 2))]
    (doseq [[idx line] (map-indexed vector lines)]
      (let [x (max 0 (quot (- width (count line)) 2))]
        (scr/put-string screen x (+ start-y idx) line
                        {:fg :red :bg :black}))))
  (scr/redraw screen))

;; ============================================================================
;; Victory Screen
;; ============================================================================

(defn format-victory-stats
  "Format the victory statistics for display.
   
   Args:
     game-state - The final game state containing:
       :stats - Optional stats map with :monsters-killed, :turns, :floors-explored
   
   Returns:
     A vector of strings to display."
  [{:keys [stats]}]
  (let [kills (get stats :monsters-killed 0)
        turns (get stats :turns 0)
        floors (get stats :floors-explored 5)]
    [""
     "=========================================="
     "              VICTORY!"
     "=========================================="
     ""
     "   You have retrieved the Amulet of Yendor!"
     ""
     (format "        Floors explored: %d" floors)
     (format "        Monsters slain: %d" kills)
     (format "        Total turns: %d" turns)
     ""
     "        Your legend will live forever!"
     ""
     "=========================================="
     ""
     "        Press any key to exit..."]))

(defn render-victory
  "Render the victory screen.
   
   Args:
     screen - The lanterna screen object
     game-state - The final game state
     width - Screen width
     height - Screen height"
  [screen game-state width height]
  (scr/clear screen)
  (let [lines (format-victory-stats game-state)
        start-y (max 0 (quot (- height (count lines)) 2))]
    (doseq [[idx line] (map-indexed vector lines)]
      (let [x (max 0 (quot (- width (count line)) 2))]
        (scr/put-string screen x (+ start-y idx) line
                        {:fg :green :bg :black}))))
  (scr/redraw screen))

;; ============================================================================
;; Combined HUD Rendering
;; ============================================================================

(defn render-hud
  "Render the complete HUD (status bar and message line).
   
   This should be called after rendering the dungeon but before
   calling redraw on the screen.
   
   Args:
     screen - The lanterna screen object
     game-state - The current game state
     width - Screen width"
  [screen game-state width]
  (render-status-bar screen game-state width)
  (render-message-line screen game-state width))

;; ============================================================================
;; Combat Message Helpers
;; ============================================================================

(defn combat-hit-message
  "Generate a message for when the player hits a monster.
   
   Args:
     monster-name - Name of the monster (e.g., 'rat', 'snake')
     damage - Amount of damage dealt
   
   Returns:
     A formatted combat message string."
  [monster-name damage]
  (format "You hit the %s for %d damage." monster-name damage))

(defn combat-kill-message
  "Generate a message for when the player kills a monster.
   
   Args:
     monster-name - Name of the monster
   
   Returns:
     A formatted kill message string."
  [monster-name]
  (format "You defeated the %s!" monster-name))

(defn combat-player-hit-message
  "Generate a message for when a monster hits the player.
   
   Args:
     monster-name - Name of the attacking monster
     damage - Amount of damage dealt
   
   Returns:
     A formatted combat message string."
  [monster-name damage]
  (let [attack-verb (case monster-name
                      "rat" "bites"
                      "snake" "strikes"
                      "goblin" "slashes"
                      "attacks")]
    (format "The %s %s you for %d damage!" monster-name attack-verb damage)))

(defn pickup-message
  "Generate a message for when the player picks something up.
   
   Args:
     item-name - Name of the item
   
   Returns:
     A formatted pickup message string."
  [item-name]
  (format "You pick up the %s." item-name))

(defn stairs-message
  "Generate a message for using stairs.
   
   Args:
     direction - :up or :down
   
   Returns:
     A formatted stairs message string."
  [direction]
  (if (= direction :down)
    "You descend the stairs..."
    "You ascend the stairs..."))

;; ============================================================================
;; Screen Size Helpers
;; ============================================================================

(defn get-dungeon-area
  "Calculate the dungeon rendering area accounting for HUD rows.
   
   Args:
     width - Total screen width
     height - Total screen height
   
   Returns:
     A map with :x :y :width :height for the dungeon area."
  [width height]
  {:x 0
   :y dungeon-start-row
   :width width
   :height (- height dungeon-start-row)})
