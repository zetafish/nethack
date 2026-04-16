(ns banking.domain
  (:require [malli.core :as m]))

(def AccountType
  [:enum "checking" "savings"])

(def CreateAccount
  [:map
   [:name [:string {:min 1 :max 255}]]
   [:account-type {:optional true} AccountType]])

(def MoneyAmount
  [:double {:min 0.01}])

(def Deposit
  [:map
   [:account-id :uuid]
   [:amount MoneyAmount]
   [:description {:optional true} [:string {:max 500}]]])

(def Withdrawal
  [:map
   [:account-id :uuid]
   [:amount MoneyAmount]
   [:description {:optional true} [:string {:max 500}]]])

(def Transfer
  [:map
   [:from-account-id :uuid]
   [:to-account-id :uuid]
   [:amount MoneyAmount]
   [:description {:optional true} [:string {:max 500}]]])

(defn validate [schema data]
  (when-not (m/validate schema data)
    (m/explain schema data)))
