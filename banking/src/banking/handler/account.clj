(ns banking.handler.account
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [java.util UUID]))

(defn- row->account [row]
  (when row
    {:id         (:accounts/id row)
     :name       (:accounts/name row)
     :type       (:accounts/type row)
     :balance    (double (:accounts/balance row))
     :created-at (str (:accounts/created_at row))}))

(defn create-account! [db {:keys [name type]}]
  (let [id (UUID/randomUUID)
        result (jdbc/execute-one!
                db
                ["INSERT INTO accounts (id, name, type, balance) VALUES (?, ?, ?, 0) RETURNING *"
                 id name type]
                {:builder-fn rs/as-unqualified-maps})]
    {:id         (:id result)
     :name       (:name result)
     :type       (:type result)
     :balance    (double (:balance result))
     :created-at (str (:created_at result))}))

(defn get-account [db id]
  (let [result (jdbc/execute-one!
                db
                ["SELECT * FROM accounts WHERE id = ?" id]
                {:builder-fn rs/as-unqualified-maps})]
    (when result
      {:id         (:id result)
       :name       (:name result)
       :type       (:type result)
       :balance    (double (:balance result))
       :created-at (str (:created_at result))})))

(defn list-accounts [db]
  (->> (jdbc/execute!
        db
        ["SELECT * FROM accounts WHERE type != 'balancing' ORDER BY created_at"]
        {:builder-fn rs/as-unqualified-maps})
       (mapv (fn [row]
               {:id         (:id row)
                :name       (:name row)
                :type       (:type row)
                :balance    (double (:balance row))
                :created-at (str (:created_at row))}))))

(defn ensure-balancing-account! [db]
  (let [existing (jdbc/execute-one!
                  db
                  ["SELECT * FROM accounts WHERE type = 'balancing'"]
                  {:builder-fn rs/as-unqualified-maps})]
    (if existing
      (:id existing)
      (let [id (UUID/randomUUID)]
        (jdbc/execute-one!
         db
         ["INSERT INTO accounts (id, name, type, balance) VALUES (?, 'Balancing Account', 'balancing', 0)"
          id])
        id))))
