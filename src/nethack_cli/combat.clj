(ns nethack-cli.combat
  "Combat system for player-monster interactions."
  (:require [nethack-cli.monster :as m]))

;; Player stats
(def player-initial-hp 20)
(def player-damage [2 4]) ;; Player deals 2-4 damage per hit

(defn create-player
  "Create initial player state at position [x y]."
  [x y]
  {:x x
   :y y
   :hp player-initial-hp
   :max-hp player-initial-hp
   :damage player-damage})

(defn player-alive?
  "Check if player is alive."
  [player]
  (> (:hp player) 0))

(defn game-over?
  "Check if game is over (player dead)."
  [player]
  (not (player-alive? player)))

(defn apply-damage-to-player
  "Apply damage to player, returns updated player."
  [player damage]
  (update player :hp #(max 0 (- % damage))))

(defn apply-damage-to-monster
  "Apply damage to a monster, returns updated monster."
  [monster damage]
  (update monster :hp #(max 0 (- % damage))))

(defn update-monster-in-list
  "Update a monster in the monsters list by id."
  [monsters monster-id update-fn]
  (mapv (fn [m]
          (if (= (:id m) monster-id)
            (update-fn m)
            m))
        monsters))

(defn player-attack
  "Player attacks a monster. Returns {:monsters updated-monsters :message str :killed? bool}"
  [monsters monster player]
  (let [damage (m/roll-damage (:damage player))
        updated-monster (apply-damage-to-monster monster damage)
        killed? (not (m/alive? updated-monster))
        updated-monsters (if killed?
                           (filterv #(not= (:id %) (:id monster)) monsters)
                           (update-monster-in-list monsters (:id monster)
                                                   (constantly updated-monster)))
        message (if killed?
                  (format "You kill the %s!" (:name monster))
                  (format "You hit the %s for %d damage." (:name monster) damage))]
    {:monsters updated-monsters
     :message message
     :killed? killed?
     :damage damage}))

(defn monster-attacks
  "Process all monster attacks on player. 
   Returns {:player updated-player :messages [str]}"
  [player attacks]
  (loop [remaining attacks
         current-player player
         messages []]
    (if (empty? remaining)
      {:player current-player :messages messages}
      (let [{:keys [damage attacker-name]} (first remaining)
            new-player (apply-damage-to-player current-player damage)
            message (format "The %s hits you for %d damage!" attacker-name damage)]
        (recur (rest remaining)
               new-player
               (conj messages message))))))

(defn bump-attack
  "Handle bump-to-attack when player moves into a monster.
   Returns nil if no monster at position, otherwise attack result."
  [monsters target-x target-y player]
  (when-let [monster (m/monster-at monsters target-x target-y)]
    (player-attack monsters monster player)))

(defn process-combat-turn
  "Process a full combat turn after player action.
   game-state should have :player :monsters keys
   walkable? predicate for terrain
   Returns updated game-state with :messages"
  [game-state walkable?]
  (let [{:keys [player monsters]} game-state
        ;; Process monster turns (AI movement and attacks)
        {:keys [monsters attacks]} (m/process-monster-turns
                                    monsters
                                    (:x player)
                                    (:y player)
                                    walkable?)
        ;; Apply monster attacks to player
        {:keys [player messages]} (monster-attacks player attacks)
        ;; Remove any dead monsters (shouldn't happen from monster attacks, but safety)
        monsters (m/remove-dead monsters)]
    (-> game-state
        (assoc :player player)
        (assoc :monsters monsters)
        (update :messages (fnil into []) messages)
        (assoc :game-over? (game-over? player)))))
