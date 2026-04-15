(ns nethack-cli.combat-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.combat :as c]
            [nethack-cli.monster :as m]))

(deftest test-player-creation
  (testing "Creating a player"
    (let [player (c/create-player 5 10)]
      (is (= 5 (:x player)))
      (is (= 10 (:y player)))
      (is (= 20 (:hp player)))
      (is (= 20 (:max-hp player)))
      (is (= [2 4] (:damage player))))))

(deftest test-player-alive
  (testing "Player with HP > 0 is alive"
    (is (c/player-alive? {:hp 1}))
    (is (c/player-alive? {:hp 20})))

  (testing "Player with HP <= 0 is dead"
    (is (not (c/player-alive? {:hp 0})))
    (is (not (c/player-alive? {:hp -5})))))

(deftest test-game-over
  (testing "Game is over when player is dead"
    (is (c/game-over? {:hp 0}))
    (is (c/game-over? {:hp -10})))

  (testing "Game is not over when player is alive"
    (is (not (c/game-over? {:hp 1})))
    (is (not (c/game-over? {:hp 20})))))

(deftest test-apply-damage-to-player
  (testing "Damage reduces HP"
    (let [player {:hp 20}
          damaged (c/apply-damage-to-player player 5)]
      (is (= 15 (:hp damaged)))))

  (testing "HP doesn't go below 0"
    (let [player {:hp 5}
          damaged (c/apply-damage-to-player player 10)]
      (is (= 0 (:hp damaged))))))

(deftest test-apply-damage-to-monster
  (testing "Damage reduces monster HP"
    (let [monster {:hp 8}
          damaged (c/apply-damage-to-monster monster 3)]
      (is (= 5 (:hp damaged)))))

  (testing "Monster HP doesn't go below 0"
    (let [monster {:hp 3}
          damaged (c/apply-damage-to-monster monster 10)]
      (is (= 0 (:hp damaged))))))

(deftest test-bump-attack
  (let [rat (m/create-monster :rat 5 5)
        snake (m/create-monster :snake 6 6)
        monsters [rat snake]
        player (c/create-player 4 5)]

    (testing "Bump attack hits monster at position"
      (let [result (c/bump-attack monsters 5 5 player)]
        (is (some? result))
        (is (string? (:message result)))
        (is (boolean? (:killed? result)))
        (is (pos? (:damage result)))))

    (testing "No attack at empty position"
      (is (nil? (c/bump-attack monsters 10 10 player))))))

(deftest test-player-attack-kills-monster
  (let [weak-monster {:id (random-uuid) :hp 1 :name "rat" :type :rat}
        monsters [weak-monster]
        player (assoc (c/create-player 0 0) :damage [5 5])] ;; guaranteed kill

    (testing "Killing a monster removes it"
      (let [result (c/player-attack monsters weak-monster player)]
        (is (:killed? result))
        (is (empty? (:monsters result)))
        (is (clojure.string/includes? (:message result) "kill"))))))

(deftest test-monster-attacks
  (let [player {:hp 20}
        attacks [{:damage 3 :attacker-name "rat"}
                 {:damage 5 :attacker-name "snake"}]]

    (testing "Multiple monster attacks reduce player HP"
      (let [result (c/monster-attacks player attacks)]
        (is (= 12 (get-in result [:player :hp])))
        (is (= 2 (count (:messages result))))))))

(deftest test-process-combat-turn
  (let [player (c/create-player 5 5)
        ;; Monster adjacent to player - will attack
        monster (m/create-monster :rat 5 6)
        game-state {:player player :monsters [monster]}
        walkable? (constantly true)]

    (testing "Combat turn processes monster attacks"
      (let [result (c/process-combat-turn game-state walkable?)]
        (is (< (get-in result [:player :hp]) 20))
        (is (vector? (:messages result)))
        (is (boolean? (:game-over? result)))))))

(deftest test-update-monster-in-list
  (let [m1 {:id 1 :hp 5}
        m2 {:id 2 :hp 5}
        m3 {:id 3 :hp 5}
        monsters [m1 m2 m3]]

    (testing "Updates correct monster by id"
      (let [result (c/update-monster-in-list monsters 2 #(assoc % :hp 1))]
        (is (= 5 (:hp (first result))))
        (is (= 1 (:hp (second result))))
        (is (= 5 (:hp (nth result 2))))))))
