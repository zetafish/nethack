(ns banking.transaction
  (:require [banking.schema :as schema]
            [banking.account :as account]))

(defn- gen-id []
  (str (java.util.UUID/randomUUID)))

(defn- now []
  (java.util.Date.))

(defn create-ledger
  "Create an in-memory transaction ledger (atom of vector)."
  []
  (atom []))

(defn- record-transaction [ledger tx]
  (swap! ledger conj tx)
  tx)

(defn deposit
  "Deposit amount into account. Uses balancing account as source.
   Returns {:ok transaction} or {:error ...}."
  [store ledger {:keys [account-id amount] :as request}]
  (if-let [errors (schema/validate schema/DepositRequest request)]
    {:error {:validation errors}}
    (let [credit-result (account/update-balance store account-id #(+ % amount))
          _ (when (:ok credit-result)
              (account/update-balance store account/balancing-account-id #(- % amount)))]
      (if (:error credit-result)
        credit-result
        (let [tx {:id (gen-id)
                  :type :deposit
                  :amount amount
                  :timestamp (now)
                  :entries [{:account-id account/balancing-account-id
                             :direction :debit
                             :amount amount}
                            {:account-id account-id
                             :direction :credit
                             :amount amount}]}]
          {:ok (record-transaction ledger tx)})))))

(defn withdraw
  "Withdraw amount from account. Uses balancing account as destination.
   Returns {:ok transaction} or {:error ...}."
  [store ledger {:keys [account-id amount] :as request}]
  (if-let [errors (schema/validate schema/WithdrawalRequest request)]
    {:error {:validation errors}}
    (let [debit-result (account/update-balance store account-id #(- % amount))]
      (if (:error debit-result)
        debit-result
        (do
          (account/update-balance store account/balancing-account-id #(+ % amount))
          (let [tx {:id (gen-id)
                    :type :withdrawal
                    :amount amount
                    :timestamp (now)
                    :entries [{:account-id account-id
                               :direction :debit
                               :amount amount}
                              {:account-id account/balancing-account-id
                               :direction :credit
                               :amount amount}]}]
            {:ok (record-transaction ledger tx)}))))))

(defn transfer
  "Transfer amount between two accounts.
   Returns {:ok transaction} or {:error ...}."
  [store ledger {:keys [from-account-id to-account-id amount] :as request}]
  (if-let [errors (schema/validate schema/TransferRequest request)]
    {:error {:validation errors}}
    (if (= from-account-id to-account-id)
      {:error {:validation "Cannot transfer to same account"}}
      (let [debit-result (account/update-balance store from-account-id #(- % amount))]
        (if (:error debit-result)
          debit-result
          (let [credit-result (account/update-balance store to-account-id #(+ % amount))]
            (if (:error credit-result)
              ;; Rollback debit
              (do (account/update-balance store from-account-id #(+ % amount))
                  credit-result)
              (let [tx {:id (gen-id)
                        :type :transfer
                        :amount amount
                        :timestamp (now)
                        :entries [{:account-id from-account-id
                                   :direction :debit
                                   :amount amount}
                                  {:account-id to-account-id
                                   :direction :credit
                                   :amount amount}]}]
                {:ok (record-transaction ledger tx)}))))))))

(defn history
  "Get transaction history for an account. Returns vector of transactions."
  [ledger account-id]
  (->> @ledger
       (filter (fn [tx]
                 (some #(= (:account-id %) account-id)
                       (:entries tx))))
       vec))

(defn all-transactions
  "Get all transactions."
  [ledger]
  @ledger)
