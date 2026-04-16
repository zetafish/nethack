(ns banking.schema.account
  (:require [malli.core :as m]))

(def AccountId :uuid)

(def CreateAccount
  [:map
   [:name :string]
   [:type [:enum "checking" "savings"]]])

(def Account
  [:map
   [:id AccountId]
   [:name :string]
   [:type [:enum "checking" "savings" "balancing"]]
   [:balance :double]
   [:created-at :string]])

(def AccountList
  [:sequential Account])
