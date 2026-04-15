(ns nethack-cli.hud-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.hud :as hud]))

;; ============================================================================
;; Message Management Tests
;; ============================================================================

(deftest add-message-test
  (testing "adding single message"
    (let [state (hud/add-message {} "Test message")]
      (is (= ["Test message"] (:messages state)))))

  (testing "messages are ordered newest-first"
    (let [state (-> {}
                    (hud/add-message "First")
                    (hud/add-message "Second")
                    (hud/add-message "Third"))]
      (is (= ["Third" "Second" "First"] (:messages state)))))

  (testing "messages are limited to max-messages"
    (let [state (reduce hud/add-message {}
                        (map #(str "Message " %) (range 10)))]
      (is (= hud/max-messages (count (:messages state))))
      (is (= "Message 9" (first (:messages state)))))))

(deftest clear-messages-test
  (let [state (-> {}
                  (hud/add-message "One")
                  (hud/add-message "Two")
                  hud/clear-messages)]
    (is (= [] (:messages state)))))

(deftest get-last-message-test
  (testing "returns nil for empty messages"
    (is (nil? (hud/get-last-message {}))))

  (testing "returns most recent message"
    (let [state (-> {}
                    (hud/add-message "Old")
                    (hud/add-message "New"))]
      (is (= "New" (hud/get-last-message state))))))

;; ============================================================================
;; Status Bar Tests
;; ============================================================================

(deftest format-status-bar-test
  (testing "formats complete player state"
    (let [state {:current-floor 3
                 :player {:x 12 :y 8 :hp 15 :max-hp 20}}]
      (is (= "Floor:3 | HP:15/20 | Pos:(12,8)"
             (hud/format-status-bar state)))))

  (testing "handles default values"
    (let [state {:player {}}]
      (is (= "Floor:1 | HP:20/20 | Pos:(0,0)"
             (hud/format-status-bar state)))))

  (testing "handles missing player"
    (let [state {}]
      (is (= "Floor:1 | HP:20/20 | Pos:(0,0)"
             (hud/format-status-bar state)))))

  (testing "floor 5 with low HP"
    (let [state {:current-floor 5
                 :player {:x 30 :y 15 :hp 3 :max-hp 20}}]
      (is (= "Floor:5 | HP:3/20 | Pos:(30,15)"
             (hud/format-status-bar state))))))

;; ============================================================================
;; Game Over Screen Tests
;; ============================================================================

(deftest format-game-over-stats-test
  (testing "includes floor number"
    (let [lines (hud/format-game-over-stats {:current-floor 3})]
      (is (some #(re-find #"floor 3" %) lines))))

  (testing "includes monster count"
    (let [lines (hud/format-game-over-stats {:stats {:monsters-killed 7}})]
      (is (some #(re-find #"Monsters slain: 7" %) lines))))

  (testing "includes turn count"
    (let [lines (hud/format-game-over-stats {:stats {:turns 150}})]
      (is (some #(re-find #"Turns survived: 150" %) lines))))

  (testing "defaults to zero stats"
    (let [lines (hud/format-game-over-stats {})]
      (is (some #(re-find #"floor 1" %) lines))
      (is (some #(re-find #"Monsters slain: 0" %) lines))
      (is (some #(re-find #"Turns survived: 0" %) lines))))

  (testing "contains GAME OVER header"
    (let [lines (hud/format-game-over-stats {})]
      (is (some #(re-find #"GAME OVER" %) lines)))))

;; ============================================================================
;; Victory Screen Tests
;; ============================================================================

(deftest format-victory-stats-test
  (testing "contains VICTORY header"
    (let [lines (hud/format-victory-stats {})]
      (is (some #(re-find #"VICTORY" %) lines))))

  (testing "mentions Amulet of Yendor"
    (let [lines (hud/format-victory-stats {})]
      (is (some #(re-find #"Amulet of Yendor" %) lines))))

  (testing "includes floors explored"
    (let [lines (hud/format-victory-stats {:stats {:floors-explored 5}})]
      (is (some #(re-find #"Floors explored: 5" %) lines))))

  (testing "includes monsters slain"
    (let [lines (hud/format-victory-stats {:stats {:monsters-killed 12}})]
      (is (some #(re-find #"Monsters slain: 12" %) lines))))

  (testing "includes total turns"
    (let [lines (hud/format-victory-stats {:stats {:turns 350}})]
      (is (some #(re-find #"Total turns: 350" %) lines)))))

;; ============================================================================
;; Combat Message Helper Tests
;; ============================================================================

(deftest combat-hit-message-test
  (is (= "You hit the rat for 3 damage."
         (hud/combat-hit-message "rat" 3)))
  (is (= "You hit the goblin for 10 damage."
         (hud/combat-hit-message "goblin" 10))))

(deftest combat-kill-message-test
  (is (= "You defeated the rat!"
         (hud/combat-kill-message "rat")))
  (is (= "You defeated the snake!"
         (hud/combat-kill-message "snake"))))

(deftest combat-player-hit-message-test
  (testing "rat bites"
    (is (= "The rat bites you for 2 damage!"
           (hud/combat-player-hit-message "rat" 2))))
  (testing "snake strikes"
    (is (= "The snake strikes you for 4 damage!"
           (hud/combat-player-hit-message "snake" 4))))
  (testing "goblin slashes"
    (is (= "The goblin slashes you for 5 damage!"
           (hud/combat-player-hit-message "goblin" 5))))
  (testing "unknown monster attacks"
    (is (= "The orc attacks you for 3 damage!"
           (hud/combat-player-hit-message "orc" 3)))))

(deftest pickup-message-test
  (is (= "You pick up the Amulet of Yendor."
         (hud/pickup-message "Amulet of Yendor")))
  (is (= "You pick up the gold."
         (hud/pickup-message "gold"))))

(deftest stairs-message-test
  (is (= "You descend the stairs..."
         (hud/stairs-message :down)))
  (is (= "You ascend the stairs..."
         (hud/stairs-message :up))))

;; ============================================================================
;; Screen Area Tests
;; ============================================================================

(deftest get-dungeon-area-test
  (testing "calculates correct dungeon area"
    (let [area (hud/get-dungeon-area 80 24)]
      (is (= 0 (:x area)))
      (is (= hud/dungeon-start-row (:y area)))
      (is (= 80 (:width area)))
      (is (= (- 24 hud/dungeon-start-row) (:height area)))))

  (testing "small screen"
    (let [area (hud/get-dungeon-area 40 10)]
      (is (= 40 (:width area)))
      (is (= (- 10 hud/dungeon-start-row) (:height area))))))
