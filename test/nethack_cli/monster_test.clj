(ns nethack-cli.monster-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.monster :as m]))

(deftest test-monster-creation
  (testing "Creating a rat monster"
    (let [rat (m/create-monster :rat 5 10)]
      (is (= :rat (:type rat)))
      (is (= \r (:symbol rat)))
      (is (= "rat" (:name rat)))
      (is (= 3 (:hp rat)))
      (is (= 3 (:max-hp rat)))
      (is (= [1 2] (:damage rat)))
      (is (= 5 (:x rat)))
      (is (= 10 (:y rat)))
      (is (uuid? (:id rat)))))

  (testing "Creating a snake monster"
    (let [snake (m/create-monster :snake 0 0)]
      (is (= :snake (:type snake)))
      (is (= \s (:symbol snake)))
      (is (= 5 (:hp snake)))
      (is (= [1 3] (:damage snake)))))

  (testing "Creating a goblin monster"
    (let [goblin (m/create-monster :goblin 3 3)]
      (is (= :goblin (:type goblin)))
      (is (= \g (:symbol goblin)))
      (is (= 8 (:hp goblin)))
      (is (= [2 4] (:damage goblin)))))

  (testing "Invalid monster type returns nil"
    (is (nil? (m/create-monster :dragon 0 0)))))

(deftest test-monster-lookup
  (let [monsters [(m/create-monster :rat 1 1)
                  (m/create-monster :snake 2 2)
                  (m/create-monster :goblin 3 3)]]
    (testing "Finding monster at position"
      (is (= :rat (:type (m/monster-at monsters 1 1))))
      (is (= :snake (:type (m/monster-at monsters 2 2))))
      (is (= :goblin (:type (m/monster-at monsters 3 3)))))

    (testing "No monster at empty position"
      (is (nil? (m/monster-at monsters 5 5))))))

(deftest test-alive-and-dead
  (testing "Monster with HP > 0 is alive"
    (is (m/alive? {:hp 1}))
    (is (m/alive? {:hp 10})))

  (testing "Monster with HP <= 0 is dead"
    (is (not (m/alive? {:hp 0})))
    (is (not (m/alive? {:hp -5}))))

  (testing "nil monster is not alive"
    (is (not (m/alive? nil)))))

(deftest test-remove-dead
  (let [alive1 {:hp 5 :id 1}
        alive2 {:hp 1 :id 2}
        dead1 {:hp 0 :id 3}
        dead2 {:hp -2 :id 4}
        monsters [alive1 dead1 alive2 dead2]]
    (testing "Remove dead monsters"
      (let [result (m/remove-dead monsters)]
        (is (= 2 (count result)))
        (is (every? m/alive? result))
        (is (= #{1 2} (set (map :id result))))))))

(deftest test-distance
  (testing "Manhattan distance calculation"
    (is (= 0 (m/distance 0 0 0 0)))
    (is (= 1 (m/distance 0 0 1 0)))
    (is (= 1 (m/distance 0 0 0 1)))
    (is (= 2 (m/distance 0 0 1 1)))
    (is (= 5 (m/distance 0 0 3 2)))
    (is (= 10 (m/distance 5 5 0 0)))))

(deftest test-adjacent
  (testing "Adjacent positions (including diagonals)"
    (is (m/adjacent? 5 5 4 4)) ;; diagonal
    (is (m/adjacent? 5 5 5 4)) ;; up
    (is (m/adjacent? 5 5 6 5)) ;; right
    (is (m/adjacent? 5 5 5 6)) ;; down
    (is (m/adjacent? 5 5 4 5)) ;; left
    (is (m/adjacent? 5 5 6 6))) ;; diagonal

  (testing "Non-adjacent positions"
    (is (not (m/adjacent? 5 5 5 5))) ;; same position
    (is (not (m/adjacent? 5 5 7 5))) ;; 2 tiles away
    (is (not (m/adjacent? 5 5 5 7)))
    (is (not (m/adjacent? 0 0 5 5)))))

(deftest test-ai-range
  (let [monster {:x 5 :y 5}]
    (testing "Monster in range of player"
      (is (m/in-ai-range? monster 5 5))    ;; same spot
      (is (m/in-ai-range? monster 5 10))   ;; exactly 5 tiles
      (is (m/in-ai-range? monster 8 7)))   ;; 3+2=5 tiles

    (testing "Monster out of range"
      (is (not (m/in-ai-range? monster 5 11)))  ;; 6 tiles
      (is (not (m/in-ai-range? monster 11 5)))
      (is (not (m/in-ai-range? monster 10 10))))))

(deftest test-roll-damage
  (testing "Damage rolls are within bounds"
    (let [damage-range [2 5]
          rolls (repeatedly 100 #(m/roll-damage damage-range))]
      (is (every? #(>= % 2) rolls))
      (is (every? #(<= % 5) rolls)))))

(deftest test-spawn-monsters
  (let [positions [[1 1] [2 2] [3 3] [4 4] [5 5] [6 6] [7 7] [8 8] [9 9] [10 10]]
        avoid #{[1 1] [2 2]}
        monsters (m/spawn-monsters positions avoid)]

    (testing "Spawns 3-5 monsters"
      (is (>= (count monsters) 3))
      (is (<= (count monsters) 5)))

    (testing "All monsters have valid types"
      (is (every? #(contains? m/monster-types (:type %)) monsters)))

    (testing "No monsters in avoided positions"
      (let [monster-positions (set (map (fn [m] [(:x m) (:y m)]) monsters))]
        (is (empty? (clojure.set/intersection monster-positions avoid)))))

    (testing "All monsters have unique ids"
      (is (= (count monsters) (count (set (map :id monsters))))))))

(deftest test-monster-ai-attack
  (let [monster (m/create-monster :rat 5 5)
        walkable? (constantly true)
        occupied? (constantly false)]
    (testing "Monster attacks when adjacent to player"
      (let [result (m/monster-ai-turn monster 5 4 walkable? occupied?)]
        (is (some? (:attack result)))
        (is (= "rat" (get-in result [:attack :attacker-name])))
        (is (pos? (get-in result [:attack :damage])))))))

(deftest test-monster-ai-move
  (let [monster (m/create-monster :rat 5 5)
        walkable? (constantly true)
        occupied? (constantly false)]
    (testing "Monster moves toward player when in range"
      (let [result (m/monster-ai-turn monster 8 5 walkable? occupied?)
            moved-monster (:monster result)]
        (is (nil? (:attack result)))
        (is (= 6 (:x moved-monster)))
        (is (= 5 (:y moved-monster)))))

    (testing "Monster stays idle when out of range"
      (let [result (m/monster-ai-turn monster 15 15 walkable? occupied?)
            moved-monster (:monster result)]
        (is (nil? (:attack result)))
        (is (= 5 (:x moved-monster)))
        (is (= 5 (:y moved-monster)))))))
