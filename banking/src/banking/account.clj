(ns banking.account
  (:require [banking.schema :as schema]))

;; Balancing account — used to balance deposits/withdrawals against an external source
(def ^:const balancing-account-id "BALANCING")

(defn- gen-id []
  (str (java.util.UUID/randomUUID)))

(defn create-store
  "Create an in-memory account store (atom of map)."
  []
  (atom {balancing-account-id {:id balancing-account-id
                               :name "Balancing Account"
                               :type :checking
                               :balance 0M}}))

(defn create-account
  "Create a new account. Returns {:ok account} or {:error ...}."
  [store {:keys [name type] :as request}]
  (if-let [errors (schema/validate schema/CreateAccountRequest request)]
    {:error {:validation errors}}
    (let [id (gen-id)
          account {:id id :name name :type type :balance 0M}]
      (swap! store assoc id account)
      {:ok account})))

(defn get-account
  "Get account by id. Returns {:ok account} or {:error ...}."
  [store id]
  (if-let [account (get @store id)]
    {:ok account}
    {:error {:not-found (str "Account not found: " id)}}))

(defn list-accounts
  "List all accounts (excluding balancing account)."
  [store]
  (->> (vals @store)
       (remove #(= (:id %) balancing-account-id))
       vec))

(defn delete-account
  "Delete an account. Only allowed if balance is zero."
  [store id]
  (cond
    (= id balancing-account-id)
    {:error {:forbidden "Cannot delete balancing account"}}

    (nil? (get @store id))
    {:error {:not-found (str "Account not found: " id)}}

    (not= 0M (:balance (get @store id)))
    {:error {:forbidden "Cannot delete account with non-zero balance"}}

    :else
    (do (swap! store dissoc id)
        {:ok true})))

(defn update-balance
  "Atomically update account balance. Returns updated account or error."
  [store id amount-fn]
  (let [result (atom nil)]
    (swap! store
           (fn [accounts]
             (if-let [account (get accounts id)]
               (let [new-balance (amount-fn (:balance account))]
                 (if (and (not= id balancing-account-id)
                          (neg? new-balance))
                   (do (reset! result {:error {:insufficient-funds "Insufficient funds"}})
                       accounts)
                   (let [updated (assoc account :balance new-balance)]
                     (reset! result {:ok updated})
                     (assoc accounts id updated))))
               (do (reset! result {:error {:not-found (str "Account not found: " id)}})
                   accounts))))
    @result))
