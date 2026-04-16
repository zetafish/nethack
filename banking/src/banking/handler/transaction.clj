(ns banking.handler.transaction
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [banking.handler.account :as account])
  (:import [java.util UUID]))

(defn- row->transaction [row]
  {:id              (:id row)
   :type            (:type row)
   :amount          (double (:amount row))
   :from-account-id (:from_account_id row)
   :to-account-id   (:to_account_id row)
   :description     (:description row)
   :created-at      (str (:created_at row))})

(defn- insert-transaction! [tx {:keys [type amount from-account-id to-account-id description]}]
  (let [id (UUID/randomUUID)]
    (jdbc/execute-one!
     tx
     ["INSERT INTO transactions (id, type, amount, from_account_id, to_account_id, description) VALUES (?, ?, ?, ?, ?, ?) RETURNING *"
      id type amount from-account-id to-account-id description]
     {:builder-fn rs/as-unqualified-maps})))

(defn- update-balance! [tx account-id delta]
  (jdbc/execute-one!
   tx
   ["UPDATE accounts SET balance = balance + ? WHERE id = ?" delta account-id]))

(defn deposit! [db {:keys [account-id amount description]}]
  (jdbc/with-transaction [tx db]
    (let [acct (account/get-account tx account-id)]
      (when-not acct
        (throw (ex-info "Account not found" {:status 404 :account-id account-id})))
      (let [bal-id (account/ensure-balancing-account! tx)]
        (update-balance! tx account-id amount)
        (update-balance! tx bal-id (- amount))
        (-> (insert-transaction! tx {:type            "deposit"
                                     :amount          amount
                                     :from-account-id nil
                                     :to-account-id   account-id
                                     :description     description})
            row->transaction)))))

(defn withdraw! [db {:keys [account-id amount description]}]
  (jdbc/with-transaction [tx db]
    (let [acct (account/get-account tx account-id)]
      (when-not acct
        (throw (ex-info "Account not found" {:status 404 :account-id account-id})))
      (when (> amount (:balance acct))
        (throw (ex-info "Insufficient funds" {:status 400
                                              :balance (:balance acct)
                                              :requested amount})))
      (let [bal-id (account/ensure-balancing-account! tx)]
        (update-balance! tx account-id (- amount))
        (update-balance! tx bal-id amount)
        (-> (insert-transaction! tx {:type            "withdrawal"
                                     :amount          amount
                                     :from-account-id account-id
                                     :to-account-id   nil
                                     :description     description})
            row->transaction)))))

(defn transfer! [db {:keys [from-account-id to-account-id amount description]}]
  (jdbc/with-transaction [tx db]
    (let [from-acct (account/get-account tx from-account-id)
          to-acct   (account/get-account tx to-account-id)]
      (when-not from-acct
        (throw (ex-info "Source account not found" {:status 404 :account-id from-account-id})))
      (when-not to-acct
        (throw (ex-info "Destination account not found" {:status 404 :account-id to-account-id})))
      (when (= from-account-id to-account-id)
        (throw (ex-info "Cannot transfer to same account" {:status 400})))
      (when (> amount (:balance from-acct))
        (throw (ex-info "Insufficient funds" {:status 400
                                              :balance (:balance from-acct)
                                              :requested amount})))
      (update-balance! tx from-account-id (- amount))
      (update-balance! tx to-account-id amount)
      (-> (insert-transaction! tx {:type            "transfer"
                                   :amount          amount
                                   :from-account-id from-account-id
                                   :to-account-id   to-account-id
                                   :description     description})
          row->transaction))))

(defn list-transactions [db account-id]
  (->> (jdbc/execute!
        db
        ["SELECT * FROM transactions WHERE from_account_id = ? OR to_account_id = ? ORDER BY created_at DESC"
         account-id account-id]
        {:builder-fn rs/as-unqualified-maps})
       (mapv row->transaction)))
