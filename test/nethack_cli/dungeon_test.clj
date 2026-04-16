(ns nethack-cli.dungeon-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.dungeon :as d]))

(deftest make-grid-test
  (testing "grid dimensions and fill"
    (let [grid (d/make-grid 10 5)]
      (is (= 5 (count grid)))
      (is (= 10 (count (first grid))))
      (is (every? #(= \# %) (flatten grid))))))

(deftest get-set-tile-test
  (let [grid (d/make-grid 10 5)
        grid' (d/set-tile grid 3 2 \.)]
    (is (= \# (d/get-tile grid 3 2)))
    (is (= \. (d/get-tile grid' 3 2)))
    (is (= \# (d/get-tile grid' 0 0)))))

(deftest make-room-test
  (testing "room within grid bounds"
    (dotimes [_ 20]
      (let [room (d/make-room 40 20 nil)]
        (is (>= (:x room) 1))
        (is (>= (:y room) 1))
        (is (< (+ (:x room) (:w room)) 40))
        (is (< (+ (:y room) (:h room)) 20))
        (is (>= (:w room) d/min-room-size))
        (is (>= (:h room) d/min-room-size))))))

(deftest rooms-overlap-test
  (testing "overlapping rooms"
    (is (true? (d/rooms-overlap? {:x 1 :y 1 :w 5 :h 5}
                                 {:x 3 :y 3 :w 5 :h 5}))))
  (testing "non-overlapping rooms"
    (is (false? (d/rooms-overlap? {:x 1 :y 1 :w 3 :h 3}
                                  {:x 10 :y 10 :w 3 :h 3})))))

(deftest room-center-test
  (is (= [5 5] (d/room-center {:x 3 :y 3 :w 4 :h 4}))))

(deftest carve-room-test
  (let [grid (d/make-grid 10 10)
        room {:x 2 :y 2 :w 3 :h 3}
        carved (d/carve-room grid room)]
    (is (= \. (d/get-tile carved 2 2)))
    (is (= \. (d/get-tile carved 4 4)))
    (is (= \# (d/get-tile carved 0 0)))
    (is (= \# (d/get-tile carved 5 5)))))

(deftest carve-corridor-test
  (testing "horizontal corridor"
    (let [grid (d/make-grid 10 10)
          carved (d/carve-h-corridor grid 2 6 5)]
      (doseq [x (range 2 7)]
        (is (= \. (d/get-tile carved x 5))))
      (is (= \# (d/get-tile carved 1 5)))
      (is (= \# (d/get-tile carved 7 5)))))
  (testing "vertical corridor"
    (let [grid (d/make-grid 10 10)
          carved (d/carve-v-corridor grid 5 2 6)]
      (doseq [y (range 2 7)]
        (is (= \. (d/get-tile carved 5 y))))
      (is (= \# (d/get-tile carved 5 1)))
      (is (= \# (d/get-tile carved 5 7))))))

(deftest flood-fill-test
  (let [grid (-> (d/make-grid 10 10)
                 (d/set-tile 1 1 \.)
                 (d/set-tile 2 1 \.)
                 (d/set-tile 3 1 \.)
                 (d/set-tile 3 2 \.)
                 (d/set-tile 5 5 \.))
        reachable (d/flood-fill grid [1 1])]
    (is (contains? reachable [1 1]))
    (is (contains? reachable [3 2]))
    (is (not (contains? reachable [5 5])))))

(deftest connected-test
  (testing "connected grid"
    (let [grid (-> (d/make-grid 5 5)
                   (d/set-tile 1 1 \.)
                   (d/set-tile 2 1 \.)
                   (d/set-tile 2 2 \.))]
      (is (true? (d/connected? grid)))))
  (testing "disconnected grid"
    (let [grid (-> (d/make-grid 5 5)
                   (d/set-tile 1 1 \.)
                   (d/set-tile 3 3 \.))]
      (is (false? (d/connected? grid))))))

(deftest generate-floor-test
  (testing "floor properties"
    (dotimes [_ 3]
      (let [{:keys [tiles rooms width height]} (d/generate-floor)]
        (is (= width d/default-width))
        (is (= height d/default-height))
        (is (= height (count tiles)))
        (is (= width (count (first tiles))))
        (is (pos? (count rooms)))
        (is (true? (d/connected? tiles)))
        (testing "has exactly one staircase"
          (let [stairs (for [y (range height)
                             x (range width)
                             :when (= \> (d/get-tile tiles x y))]
                         [x y])]
            (is (= 1 (count stairs)))))))))

(deftest generate-floor-custom-size-test
  (let [{:keys [width height tiles]} (d/generate-floor 50 25)]
    (is (= 50 width))
    (is (= 25 height))
    (is (= 25 (count tiles)))
    (is (= 50 (count (first tiles))))))
