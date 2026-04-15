(ns nethack-cli.core
  "NetHack CLI - A minimal roguelike game.
   
   Main entry point that initializes the game loop and manages the
   terminal screen using lanterna."
  (:require [lanterna.screen :as s]
            [nethack-cli.victory :as victory])
  (:gen-class))

;; ============================================================================
;; Game State Management
;; ============================================================================

(defn create-initial-state
  "Creates the initial game state with all required fields."
  []
  (merge
   {:player {:x 5 :y 5 :hp 20 :max-hp 20}
    :floors []
    :current-floor 0
    :messages []
    :game-over false
    :quit false}
   (victory/init-victory-state)))

;; ============================================================================
;; Game Loop Integration Points
;; ============================================================================

(defn process-turn
  "Processes a single game turn after player action.
   Checks for victory condition (amulet pickup) and other state changes.
   
   This function should be called after each player move."
  [game-state]
  (-> game-state
      victory/pickup-amulet
      victory/check-victory))

(defn handle-game-end
  "Handles end-game conditions (victory or game over).
   Returns true if the game has ended."
  [screen game-state]
  (when (or (:victory game-state) (:game-over game-state))
    (victory/handle-end-game screen game-state)
    true))

;; ============================================================================
;; Welcome Screen
;; ============================================================================

(defn draw-welcome-screen
  "Draws the welcome screen."
  [screen]
  (let [[cols rows] (s/get-size screen)
        title "Welcome to NetHack CLI"
        subtitle "Press any key to begin, or 'q' to quit."
        title-x (max 0 (quot (- cols (count title)) 2))
        title-y (max 0 (quot rows 2))
        sub-x (max 0 (quot (- cols (count subtitle)) 2))
        sub-y (inc title-y)]
    (s/clear screen)
    (s/put-string screen title-x title-y title)
    (s/put-string screen sub-x sub-y subtitle)
    (s/redraw screen)))

(defn wait-for-start
  "Waits on the welcome screen for player to start or quit.
   Returns true if the player wants to start, false if quit."
  [screen]
  (draw-welcome-screen screen)
  (loop []
    (let [key (s/get-key-blocking screen)]
      (cond
        (= key \q) false
        :else true))))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn -main
  "Main entry point for NetHack CLI."
  [& _args]
  (let [scr (s/get-screen :unix)]
    (s/start scr)
    (try
      (when (wait-for-start scr)
        ;; Initialize game state
        (let [initial-state (create-initial-state)]
          ;; Game loop would go here - for now just demonstrate victory screen
          ;; with a mock "won" state for testing
          (when-let [demo-mode (System/getenv "NETHACK_DEMO_VICTORY")]
            (let [won-state (assoc initial-state
                                   :victory true
                                   :has-amulet true
                                   :monsters-killed 12
                                   :floors [{:visited true}
                                            {:visited true}
                                            {:visited true}
                                            {:visited true}
                                            {:visited true}])]
              (victory/show-victory-screen scr won-state)))))
      (finally
        (s/stop scr)))))
