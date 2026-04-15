(ns nethack-cli.victory-test
  (:require [clojure.test :refer [deftest is testing]]
            [nethack-cli.victory :as v]
            [nethack-cli.dungeon :as dungeon]))

;; ============================================================================
;; Test Fixtures
;; ============================================================================

(defn make-test-floor
  "Create a small test floor with known layout."
  []
  {:tiles [[\# \# \# \# \#]
           [\# \. \. \. \#]
           [\# \. \. \. \#]
           [\# \. \. \> \#]
           [\# \# \# \# \#]]
   :rooms [{:x 1 :y 1 :w 3 :h 3}]
   :width 5
   :height 5})

(defn make-game-state
  "Create a test game state."
  [& {:keys [px py amulet-pos floors current-floor has-amulet monsters-killed]
      :or {px 2 py 2 amulet-pos nil floors nil current-floor 0
           has-amulet false monsters-killed 0}}]
  (let [floor (cond-> (make-test-floor)
                amulet-pos (assoc :amulet-pos amulet-pos))
        floor (if floors nil floor)]
    {:player {:x px :y py :hp 20}
     :floors (or floors [floor])
     :current-floor current-floor
     :messages []
     :has-amulet has-amulet
     :victory false
     :monsters-killed monsters-killed}))

;; ============================================================================
;; Amulet Placement Tests
;; ============================================================================

(deftest place-amulet-test
  (testing "places amulet on floor 5"
    (let [floor (make-test-floor)
          result (v/place-amulet floor 5)]
      (is (some? (:amulet-pos result)))
      (let [[ax ay] (:amulet-pos result)]
        (is (= \. (dungeon/get-tile (:tiles floor) ax ay))))))

  (testing "does not place amulet on other floors"
    (let [floor (make-test-floor)]
      (doseq [n [1 2 3 4]]
        (let [result (v/place-amulet floor n)]
          (is (nil? (:amulet-pos result)))))))

  (testing "amulet position is on floor tile"
    (dotimes [_ 10]
      (let [floor (dungeon/generate-floor)
            result (v/place-amulet floor 5)
            [ax ay] (:amulet-pos result)]
        (is (= \. (dungeon/get-tile (:tiles floor) ax ay)))))))

(deftest has-amulet?-test
  (testing "returns true when amulet is present"
    (is (true? (v/has-amulet? {:amulet-pos [2 2]}))))

  (testing "returns false when no amulet"
    (is (false? (v/has-amulet? {})))
    (is (false? (v/has-amulet? {:amulet-pos nil})))))

(deftest amulet-at?-test
  (let [floor {:amulet-pos [3 3]}]
    (testing "returns true when at amulet position"
      (is (true? (v/amulet-at? floor 3 3))))

    (testing "returns false when not at amulet position"
      (is (false? (v/amulet-at? floor 2 2)))
      (is (false? (v/amulet-at? floor 3 2))))))

;; ============================================================================
;; Amulet Pickup Tests
;; ============================================================================

(deftest pickup-amulet-test
  (testing "picks up amulet when standing on it"
    (let [state (make-game-state :px 2 :py 2 :amulet-pos [2 2])
          result (v/pickup-amulet state)]
      (is (true? (:has-amulet result)))
      (is (true? (:victory result)))
      (is (nil? (get-in result [:floors 0 :amulet-pos])))
      (is (some #(re-find #"Amulet" %) (:messages result)))))

  (testing "does not pick up amulet when not standing on it"
    (let [state (make-game-state :px 1 :py 1 :amulet-pos [3 3])
          result (v/pickup-amulet state)]
      (is (false? (:has-amulet result)))
      (is (false? (:victory result)))
      (is (= [3 3] (get-in result [:floors 0 :amulet-pos])))))

  (testing "handles floor without amulet"
    (let [state (make-game-state :px 2 :py 2 :amulet-pos nil)
          result (v/pickup-amulet state)]
      (is (false? (:has-amulet result)))
      (is (false? (:victory result))))))

(deftest check-victory-test
  (testing "sets victory when has amulet"
    (let [state {:has-amulet true :victory false}
          result (v/check-victory state)]
      (is (true? (:victory result)))))

  (testing "does not set victory without amulet"
    (let [state {:has-amulet false :victory false}
          result (v/check-victory state)]
      (is (false? (:victory result))))))

;; ============================================================================
;; Score Calculation Tests
;; ============================================================================

(deftest calculate-score-test
  (testing "calculates score with all components"
    (let [state {:floors [{:visited true}
                          {:visited true}
                          {:visited true}
                          {:visited false}
                          {:visited false}]
                 :has-amulet true
                 :monsters-killed 10}
          score (v/calculate-score state)]
      (is (= 3 (:floors-explored score)))
      (is (= 10 (:monsters-killed score)))
      (is (= 300 (:floor-points score)))
      (is (= 500 (:monster-points score)))
      (is (= 1000 (:amulet-bonus score)))
      (is (= 1800 (:total score)))))

  (testing "no amulet bonus without amulet"
    (let [state {:floors [{:visited true}]
                 :has-amulet false
                 :monsters-killed 5}
          score (v/calculate-score state)]
      (is (= 0 (:amulet-bonus score)))
      (is (= 350 (:total score)))))

  (testing "handles empty/missing values"
    (let [state {:floors []
                 :has-amulet false}
          score (v/calculate-score state)]
      (is (= 0 (:floors-explored score)))
      (is (= 0 (:monsters-killed score)))
      (is (= 0 (:total score))))))

;; ============================================================================
;; Initial State Tests
;; ============================================================================

(deftest init-victory-state-test
  (let [state (v/init-victory-state)]
    (is (false? (:has-amulet state)))
    (is (false? (:victory state)))
    (is (= 0 (:monsters-killed state)))))

;; ============================================================================
;; Constants Tests
;; ============================================================================

(deftest constants-test
  (testing "amulet appears on floor 5"
    (is (= 5 v/amulet-floor)))

  (testing "total floors is 5"
    (is (= 5 v/total-floors)))

  (testing "amulet character is double-quote"
    (is (= \" v/amulet-char))))
