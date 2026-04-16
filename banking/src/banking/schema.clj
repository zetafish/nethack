(ns banking.schema
  (:require [malli.core :as m]
            [malli.error :as me]))

;; --- Custom schemas ---

(def PositiveAmount
  [:and number? [:fn {:error/message "must be positive"} pos?]])

(def NonNegativeAmount
  [:and number? [:fn {:error/message "must be non-negative"} #(not (neg? %))]])

;; --- Account schemas ---

(def AccountType
  [:enum :checking :savings])

(def AccountId
  [:string {:min 1}])

(def Account
  [:map
   [:id AccountId]
   [:name [:string {:min 1}]]
   [:type AccountType]
   [:balance NonNegativeAmount]])

(def CreateAccountRequest
  [:map
   [:name [:string {:min 1}]]
   [:type AccountType]])

;; --- Transaction schemas ---

(def TransactionType
  [:enum :deposit :withdrawal :transfer])

(def Transaction
  [:map
   [:id [:string {:min 1}]]
   [:type TransactionType]
   [:amount PositiveAmount]
   [:timestamp inst?]
   [:entries [:vector
              [:map
               [:account-id AccountId]
               [:direction [:enum :debit :credit]]
               [:amount PositiveAmount]]]]])

(def DepositRequest
  [:map
   [:account-id AccountId]
   [:amount PositiveAmount]])

(def WithdrawalRequest
  [:map
   [:account-id AccountId]
   [:amount PositiveAmount]])

(def TransferRequest
  [:map
   [:from-account-id AccountId]
   [:to-account-id AccountId]
   [:amount PositiveAmount]])

;; --- Validation ---

(defn validate
  "Validate data against schema. Returns nil if valid, error map if invalid."
  [schema data]
  (when-not (m/validate schema data)
    (-> (m/explain schema data)
        me/humanize)))
