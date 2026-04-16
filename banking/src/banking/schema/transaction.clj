(ns banking.schema.transaction)

(def uuid-string
  [:string {:min 36 :max 36}])

(def DepositRequest
  [:map
   [:account-id uuid-string]
   [:amount [:double {:min 0.01}]]
   [:description {:optional true} :string]])

(def WithdrawRequest
  [:map
   [:account-id uuid-string]
   [:amount [:double {:min 0.01}]]
   [:description {:optional true} :string]])

(def TransferRequest
  [:map
   [:from-account-id uuid-string]
   [:to-account-id uuid-string]
   [:amount [:double {:min 0.01}]]
   [:description {:optional true} :string]])

(def Transaction
  [:map
   [:id :uuid]
   [:type [:enum "deposit" "withdrawal" "transfer"]]
   [:amount :double]
   [:from-account-id {:optional true} [:maybe :uuid]]
   [:to-account-id {:optional true} [:maybe :uuid]]
   [:description {:optional true} [:maybe :string]]
   [:created-at :string]])

(def TransactionList
  [:sequential Transaction])
