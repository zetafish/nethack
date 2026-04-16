(ns banking.test-helpers
  (:require [integrant.core :as ig]
            [aero.core :as aero]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]))

(defn test-config []
  (-> (io/resource "test-config.edn")
      (aero/read-config)))

(defn start-test-system []
  (-> (test-config)
      ig/prep
      ig/init))

(defn with-system [f]
  (let [system (start-test-system)]
    (try
      (f system)
      (finally
        (ig/halt! system)))))

(defn clean-tables! [ds]
  (jdbc/execute! ds ["DELETE FROM transactions"])
  (jdbc/execute! ds ["DELETE FROM accounts WHERE account_type != 'balancing'"]))

(defn with-clean-db [ds f]
  (clean-tables! ds)
  (f))
