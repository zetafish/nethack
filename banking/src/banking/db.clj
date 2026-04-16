(ns banking.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [com.zaxxer.hikari HikariDataSource HikariConfig]))

(def balancing-account-id "00000000-0000-0000-0000-000000000000")

(defn create-datasource [jdbc-url]
  (let [config (doto (HikariConfig.)
                 (.setJdbcUrl jdbc-url)
                 (.setMaximumPoolSize 10))]
    (HikariDataSource. config)))

(defn- query-opts []
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-account! [ds {:keys [name account-type]}]
  (jdbc/execute-one! ds
                     ["INSERT INTO accounts (name, account_type) VALUES (?, ?) RETURNING *"
                      name (or account-type "checking")]
                     (query-opts)))

(defn get-account [ds id]
  (jdbc/execute-one! ds
                     ["SELECT * FROM accounts WHERE id = ?::uuid" id]
                     (query-opts)))

(defn list-accounts [ds]
  (jdbc/execute! ds
                 ["SELECT * FROM accounts WHERE id != ?::uuid ORDER BY created_at"
                  balancing-account-id]
                 (query-opts)))

(defn transfer! [ds {:keys [from-account-id to-account-id amount description]}]
  (jdbc/with-transaction [tx ds]
    (jdbc/execute-one! tx
                       ["UPDATE accounts SET balance = balance - ?, updated_at = now() WHERE id = ?::uuid"
                        amount from-account-id])
    (jdbc/execute-one! tx
                       ["UPDATE accounts SET balance = balance + ?, updated_at = now() WHERE id = ?::uuid"
                        amount to-account-id])
    (jdbc/execute-one! tx
                       ["INSERT INTO transactions (debit_account, credit_account, amount, description)
        VALUES (?::uuid, ?::uuid, ?, ?) RETURNING *"
                        from-account-id to-account-id amount description]
                       (query-opts))))

(defn deposit! [ds {:keys [account-id amount description]}]
  (transfer! ds {:from-account-id balancing-account-id
                 :to-account-id   account-id
                 :amount          amount
                 :description     (or description "Deposit")}))

(defn withdraw! [ds {:keys [account-id amount description]}]
  (transfer! ds {:from-account-id account-id
                 :to-account-id   balancing-account-id
                 :amount          amount
                 :description     (or description "Withdrawal")}))

(defn get-transactions [ds account-id]
  (jdbc/execute! ds
                 ["SELECT * FROM transactions
      WHERE debit_account = ?::uuid OR credit_account = ?::uuid
      ORDER BY created_at DESC"
                  account-id account-id]
                 (query-opts)))
