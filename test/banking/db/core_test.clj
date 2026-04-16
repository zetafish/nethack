(ns banking.db.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [banking.test-helpers :as helpers]
            [banking.db.core :as db]))

(def ^:dynamic *ds* nil)

(defn system-fixture [f]
  (helpers/with-system
    (fn [system]
      (binding [*ds* (:db/postgres system)]
        (helpers/with-clean-db *ds* f)))))

(use-fixtures :each system-fixture)

(deftest health-check-test
  (testing "database is reachable"
    (let [result (db/execute-one! *ds* ["SELECT 1 AS result"])]
      (is (= 1 (:result result))))))
