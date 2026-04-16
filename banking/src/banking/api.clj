(ns banking.api
  (:require [compojure.api.sweet :refer [api context GET POST]]
            [ring.util.http-response :as resp]
            [banking.db :as db]
            [banking.domain :as domain]))

(defn- parse-uuid [s]
  (try (java.util.UUID/fromString s) (catch Exception _ nil)))

(defn- validate-and-run [schema data f]
  (if-let [errors (domain/validate schema data)]
    (resp/bad-request {:errors (str errors)})
    (f data)))

(defn app [ds]
  (api
   {:swagger {:ui "/swagger"
              :spec "/swagger.json"
              :data {:info {:title "Banking API"
                            :version "1.0.0"}}}}

   (context "/api/accounts" []
     (GET "/" []
       (resp/ok (db/list-accounts ds)))

     (POST "/" {body :body-params}
       (validate-and-run domain/CreateAccount body
                         (fn [data]
                           (->> data
                                (db/create-account! ds)
                                resp/created))))

     (GET "/:id" [id]
       (if-let [account (db/get-account ds id)]
         (resp/ok account)
         (resp/not-found {:error "Account not found"})))

     (GET "/:id/transactions" [id]
       (if (db/get-account ds id)
         (resp/ok (db/get-transactions ds id))
         (resp/not-found {:error "Account not found"}))))

   (context "/api/transactions" []
     (POST "/deposit" {body :body-params}
       (let [data (-> body
                      (update :account-id #(if (string? %) (parse-uuid %) %))
                      (update :amount double))]
         (validate-and-run domain/Deposit data
                           (fn [d]
                             (if (db/get-account ds (str (:account-id d)))
                               (->> d
                                    (db/deposit! ds)
                                    resp/created)
                               (resp/not-found {:error "Account not found"}))))))

     (POST "/withdraw" {body :body-params}
       (let [data (-> body
                      (update :account-id #(if (string? %) (parse-uuid %) %))
                      (update :amount double))]
         (validate-and-run domain/Withdrawal data
                           (fn [d]
                             (let [account (db/get-account ds (str (:account-id d)))]
                               (cond
                                 (nil? account)
                                 (resp/not-found {:error "Account not found"})

                                 (< (:balance account) (:amount d))
                                 (resp/bad-request {:error "Insufficient funds"})

                                 :else
                                 (->> d
                                      (db/withdraw! ds)
                                      resp/created)))))))

     (POST "/transfer" {body :body-params}
       (let [data (-> body
                      (update :from-account-id #(if (string? %) (parse-uuid %) %))
                      (update :to-account-id #(if (string? %) (parse-uuid %) %))
                      (update :amount double))]
         (validate-and-run domain/Transfer data
                           (fn [d]
                             (let [from (db/get-account ds (str (:from-account-id d)))
                                   to   (db/get-account ds (str (:to-account-id d)))]
                               (cond
                                 (nil? from)
                                 (resp/not-found {:error "Source account not found"})

                                 (nil? to)
                                 (resp/not-found {:error "Destination account not found"})

                                 (< (:balance from) (:amount d))
                                 (resp/bad-request {:error "Insufficient funds"})

                                 :else
                                 (->> d
                                      (db/transfer! ds)
                                      resp/created))))))))))
