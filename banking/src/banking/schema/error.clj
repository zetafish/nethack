(ns banking.schema.error)

(def ErrorResponse
  [:map
   [:error :string]
   [:details {:optional true} :any]])
