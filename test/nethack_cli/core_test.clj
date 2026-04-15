(ns nethack-cli.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.core :as core]
            [nethack-cli.dungeon :as dungeon]))

(defn make-test-floor
  "Create a small test floor with known layout.
   5x5 grid: walls around edge, floor inside, stairs at [3 3]."
  []
  (let [grid (-> (dungeon/make-grid 5 5)
                 (dungeon/set-tile 1 1 \.)
                 (dungeon/set-tile 2 1 \.)
                 (dungeon/set-tile 3 1 \.)
                 (dungeon/set-tile 1 2 \.)
                 (dungeon/set-tile 2 2 \.)
                 (dungeon/set-tile 3 2 \.)
                 (dungeon/set-tile 1 3 \.)
                 (dungeon/set-tile 2 3 \.)
                 (dungeon/set-tile 3 3 \>))]
    {:tiles grid
     :rooms [{:x 1 :y 1 :w 3 :h 3}]
     :width 5
     :height 5}))

(defn make-state [floor px py]
  {:player {:x px :y py :hp 20}
   :floor floor})

(deftest key->direction-test
  (testing "vi keys"
    (is (= [-1 0] (core/key->direction \h)))
    (is (= [0 1] (core/key->direction \j)))
    (is (= [0 -1] (core/key->direction \k)))
    (is (= [1 0] (core/key->direction \l))))
  (testing "arrow keys"
    (is (= [-1 0] (core/key->direction :left)))
    (is (= [1 0] (core/key->direction :right)))
    (is (= [0 -1] (core/key->direction :up)))
    (is (= [0 1] (core/key->direction :down))))
  (testing "unknown key returns nil"
    (is (nil? (core/key->direction \x)))
    (is (nil? (core/key->direction :enter)))))

(deftest move-player-test
  (let [floor (make-test-floor)]
    (testing "move to open floor"
      (let [state (make-state floor 2 2)
            moved (core/move-player state 1 0)]
        (is (= 3 (get-in moved [:player :x])))
        (is (= 2 (get-in moved [:player :y])))))

    (testing "move blocked by wall"
      (let [state (make-state floor 1 1)
            moved (core/move-player state -1 0)]
        (is (= 1 (get-in moved [:player :x])))
        (is (= 1 (get-in moved [:player :y])))))

    (testing "move onto stairs is allowed"
      (let [state (make-state floor 2 3)
            moved (core/move-player state 1 0)]
        (is (= 3 (get-in moved [:player :x])))
        (is (= 3 (get-in moved [:player :y])))))

    (testing "move up blocked by wall"
      (let [state (make-state floor 2 1)
            moved (core/move-player state 0 -1)]
        (is (= 2 (get-in moved [:player :x])))
        (is (= 1 (get-in moved [:player :y])))))

    (testing "move preserves hp"
      (let [state (make-state floor 2 2)
            moved (core/move-player state 1 0)]
        (is (= 20 (get-in moved [:player :hp])))))))

(deftest walkable-test
  (let [floor (make-test-floor)
        tiles (:tiles floor)]
    (testing "floor tiles are walkable"
      (is (true? (dungeon/walkable? tiles 2 2))))
    (testing "walls are not walkable"
      (is (false? (dungeon/walkable? tiles 0 0))))
    (testing "stairs are walkable"
      (is (true? (dungeon/walkable? tiles 3 3))))
    (testing "out of bounds not walkable"
      (is (not (dungeon/walkable? tiles -1 0))))))

(deftest player-start-test
  (testing "player starts inside first room"
    (dotimes [_ 10]
      (let [floor (dungeon/generate-floor)
            [px py] (dungeon/player-start floor)
            room (first (:rooms floor))]
        (is (>= px (:x room)))
        (is (< px (+ (:x room) (:w room))))
        (is (>= py (:y room)))
        (is (< py (+ (:y room) (:h room))))
        (is (true? (dungeon/walkable? (:tiles floor) px py)))))))
