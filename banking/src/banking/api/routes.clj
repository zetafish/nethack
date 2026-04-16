(ns banking.api.routes
  (:require [compojure.api.sweet :refer [api context GET POST]]
            [ring.util.http-response :as response]
            [malli.core :as m]
            [malli.error :as me]
            [banking.handler.account :as account]
            [banking.handler.transaction :as transaction]
            [banking.schema.account :as account-schema]
            [banking.schema.transaction :as tx-schema]
            [clojure.data.json :as json])
  (:import [java.util UUID]))

(defn- str->uuid [s]
  (try (UUID/fromString s) (catch Exception _ nil)))

(defn- validate [schema data]
  (when-not (m/validate schema data)
    (throw (ex-info "Validation error"
                    {:status 400
                     :errors (-> (m/explain schema data)
                                 me/humanize)}))))

(defn- parse-body [request]
  (let [body-params (:body-params request)]
    (if (and body-params (not-empty body-params))
      body-params
      (some-> request :body slurp (json/read-str :key-fn keyword)))))

(defn- coerce-uuid-keys [body ks]
  (reduce (fn [m k]
            (if-let [v (get m k)]
              (if (string? v)
                (assoc m k (UUID/fromString v))
                m)
              m))
          body ks))

(defn app-routes [db]
  (api
   {:swagger    {:ui   "/swagger"
                 :spec "/swagger.json"
                 :data {:info {:title "Banking API" :version "1.0.0"}}}
    :exceptions {:handlers
                 {:compojure.api.exception/default
                  (fn [^Exception e _ _]
                    (if (instance? clojure.lang.ExceptionInfo e)
                      (let [{:keys [status errors]} (ex-data e)]
                        (case status
                          400 (response/bad-request {:error (.getMessage e) :details errors})
                          404 (response/not-found {:error (.getMessage e)})
                          (response/internal-server-error {:error "Internal server error"})))
                      (response/internal-server-error {:error "Internal server error"})))}}}

   (context "/api" []

     (context "/accounts" []

       (POST "/" request
         (let [body (parse-body request)]
           (validate account-schema/CreateAccount body)
           (let [acct (account/create-account! db body)]
             (response/created (str "/api/accounts/" (:id acct)) acct))))

       (GET "/" []
         (response/ok (account/list-accounts db)))

       (GET "/:id" [id]
         (let [uuid (str->uuid id)]
           (if-not uuid
             (response/bad-request {:error "Invalid account ID"})
             (if-let [acct (account/get-account db uuid)]
               (response/ok acct)
               (response/not-found {:error "Account not found"}))))))

     (context "/transactions" []

       (POST "/deposit" request
         (let [body (parse-body request)]
           (validate tx-schema/DepositRequest body)
           (let [body (coerce-uuid-keys body [:account-id])]
             (try
               (response/created "/api/transactions" (transaction/deposit! db body))
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [status] :as data} (ex-data e)]
                   (case status
                     404 (response/not-found {:error (.getMessage e)})
                     (response/bad-request {:error (.getMessage e) :details data}))))))))

       (POST "/withdraw" request
         (let [body (parse-body request)]
           (validate tx-schema/WithdrawRequest body)
           (let [body (coerce-uuid-keys body [:account-id])]
             (try
               (response/created "/api/transactions" (transaction/withdraw! db body))
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [status] :as data} (ex-data e)]
                   (case status
                     404 (response/not-found {:error (.getMessage e)})
                     400 (response/bad-request {:error (.getMessage e) :details data})
                     (response/internal-server-error {:error (.getMessage e)}))))))))

       (POST "/transfer" request
         (let [body (parse-body request)]
           (validate tx-schema/TransferRequest body)
           (let [body (coerce-uuid-keys body [:from-account-id :to-account-id])]
             (try
               (response/created "/api/transactions" (transaction/transfer! db body))
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [status] :as data} (ex-data e)]
                   (case status
                     404 (response/not-found {:error (.getMessage e)})
                     400 (response/bad-request {:error (.getMessage e) :details data})
                     (response/internal-server-error {:error (.getMessage e)}))))))))

       (GET "/:account-id" [account-id]
         (let [uuid (str->uuid account-id)]
           (if-not uuid
             (response/bad-request {:error "Invalid account ID"})
             (response/ok (transaction/list-transactions db uuid)))))))))

(defn wrap-exception-handler [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [status errors]} (ex-data e)]
          (case status
            400 (response/bad-request {:error (.getMessage e) :details errors})
            404 (response/not-found {:error (.getMessage e)})
            (response/internal-server-error {:error "Internal server error"}))))
      (catch Exception _
        (response/internal-server-error {:error "Internal server error"})))))
