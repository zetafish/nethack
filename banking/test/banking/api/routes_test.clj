(ns banking.api.routes-test
  (:require [clojure.test :refer [deftest testing is]]
            [banking.api.routes :as routes]
            [banking.handler.account :as account]
            [banking.handler.transaction :as transaction]
            [ring.mock.request :as mock]
            [clojure.data.json :as json])
  (:import [java.util UUID]))

;; --- In-memory mock state ---

(defn fresh-state []
  {:accounts     (atom {})
   :transactions (atom [])
   :balancing-id (atom nil)})

(defn mock-create-account! [state {:keys [name type]}]
  (let [id   (UUID/randomUUID)
        acct {:id id :name name :type type :balance 0.0 :created-at "2024-01-01T00:00:00"}]
    (swap! (:accounts state) assoc id acct)
    acct))

(defn mock-get-account [state id]
  (get @(:accounts state) id))

(defn mock-list-accounts [state]
  (->> (vals @(:accounts state))
       (remove #(= "balancing" (:type %)))
       vec))

(defn mock-ensure-balancing! [state]
  (or @(:balancing-id state)
      (let [id (UUID/randomUUID)]
        (reset! (:balancing-id state) id)
        (swap! (:accounts state) assoc id
               {:id id :name "Balancing" :type "balancing" :balance 0.0 :created-at "2024-01-01T00:00:00"})
        id)))

(defn mock-deposit! [state {:keys [account-id amount description]}]
  (let [acct (mock-get-account state account-id)]
    (when-not acct (throw (ex-info "Account not found" {:status 404})))
    (let [bal-id (mock-ensure-balancing! state)
          tx     {:id (UUID/randomUUID) :type "deposit" :amount amount
                  :from-account-id nil :to-account-id account-id
                  :description description :created-at "2024-01-01T00:00:00"}]
      (swap! (:accounts state) update-in [account-id :balance] + amount)
      (swap! (:accounts state) update-in [bal-id :balance] - amount)
      (swap! (:transactions state) conj tx)
      tx)))

(defn mock-withdraw! [state {:keys [account-id amount]}]
  (let [acct (mock-get-account state account-id)]
    (when-not acct (throw (ex-info "Account not found" {:status 404})))
    (when (> amount (:balance acct))
      (throw (ex-info "Insufficient funds" {:status 400 :balance (:balance acct) :requested amount})))
    (let [bal-id (mock-ensure-balancing! state)
          tx     {:id (UUID/randomUUID) :type "withdrawal" :amount amount
                  :from-account-id account-id :to-account-id nil
                  :description nil :created-at "2024-01-01T00:00:00"}]
      (swap! (:accounts state) update-in [account-id :balance] - amount)
      (swap! (:accounts state) update-in [bal-id :balance] + amount)
      (swap! (:transactions state) conj tx)
      tx)))

(defn mock-transfer! [state {:keys [from-account-id to-account-id amount]}]
  (let [from (mock-get-account state from-account-id)
        to   (mock-get-account state to-account-id)]
    (when-not from (throw (ex-info "Source account not found" {:status 404})))
    (when-not to (throw (ex-info "Destination account not found" {:status 404})))
    (when (= from-account-id to-account-id)
      (throw (ex-info "Cannot transfer to same account" {:status 400})))
    (when (> amount (:balance from))
      (throw (ex-info "Insufficient funds" {:status 400})))
    (let [tx {:id (UUID/randomUUID) :type "transfer" :amount amount
              :from-account-id from-account-id :to-account-id to-account-id
              :description nil :created-at "2024-01-01T00:00:00"}]
      (swap! (:accounts state) update-in [from-account-id :balance] - amount)
      (swap! (:accounts state) update-in [to-account-id :balance] + amount)
      (swap! (:transactions state) conj tx)
      tx)))

(defn mock-list-transactions [state account-id]
  (->> @(:transactions state)
       (filter #(or (= account-id (:from-account-id %))
                    (= account-id (:to-account-id %))))
       vec))

;; --- Helpers ---

(defn parse-body [response]
  (let [body (:body response)]
    (cond
      (string? body)                        (json/read-str body :key-fn keyword)
      (map? body)                           body
      (sequential? body)                    body
      (instance? java.io.InputStream body)  (json/read (java.io.InputStreamReader. body) :key-fn keyword)
      :else                                 body)))

(defn json-request [method path body]
  (-> (mock/request method path)
      (mock/content-type "application/json")
      (mock/body (json/write-str body))))

;; --- Tests ---

(deftest test-create-account
  (let [state (fresh-state)]
    (with-redefs [account/create-account! (fn [_ data] (mock-create-account! state data))]
      (let [handler (routes/app-routes nil)
            resp    (handler (json-request :post "/api/accounts" {:name "Test" :type "checking"}))]
        (is (= 201 (:status resp)))
        (let [body (parse-body resp)]
          (is (= "Test" (:name body)))
          (is (= "checking" (:type body)))
          (is (= 0.0 (:balance body))))))))

(deftest test-list-accounts
  (let [state (fresh-state)
        _     (mock-create-account! state {:name "A" :type "checking"})
        _     (mock-create-account! state {:name "B" :type "savings"})]
    (with-redefs [account/list-accounts (fn [_] (mock-list-accounts state))]
      (let [handler (routes/app-routes nil)
            resp    (handler (mock/request :get "/api/accounts"))]
        (is (= 200 (:status resp)))
        (is (= 2 (count (parse-body resp))))))))

(deftest test-get-account
  (let [state (fresh-state)
        acct  (mock-create-account! state {:name "Test" :type "checking"})]
    (with-redefs [account/get-account (fn [_ id] (mock-get-account state id))]
      (let [handler (routes/app-routes nil)
            resp    (handler (mock/request :get (str "/api/accounts/" (:id acct))))]
        (is (= 200 (:status resp)))
        (is (= "Test" (:name (parse-body resp))))))))

(deftest test-get-account-not-found
  (with-redefs [account/get-account (fn [_ _] nil)]
    (let [handler (routes/app-routes nil)
          resp    (handler (mock/request :get (str "/api/accounts/" (UUID/randomUUID))))]
      (is (= 404 (:status resp))))))

(deftest test-deposit
  (let [state (fresh-state)
        acct  (mock-create-account! state {:name "Test" :type "checking"})]
    (with-redefs [transaction/deposit! (fn [_ data] (mock-deposit! state data))]
      (let [handler (routes/app-routes nil)
            resp    (handler (json-request :post "/api/transactions/deposit"
                                           {:account-id (str (:id acct)) :amount 100.0}))]
        (is (= 201 (:status resp)))
        (is (= "deposit" (:type (parse-body resp))))
        (is (= 100.0 (:balance (mock-get-account state (:id acct)))))))))

(deftest test-withdraw-insufficient-funds
  (let [state (fresh-state)
        acct  (mock-create-account! state {:name "Test" :type "checking"})]
    (with-redefs [transaction/withdraw! (fn [_ data] (mock-withdraw! state data))]
      (let [handler (routes/app-routes nil)
            resp    (handler (json-request :post "/api/transactions/withdraw"
                                           {:account-id (str (:id acct)) :amount 100.0}))]
        (is (= 400 (:status resp)))))))

(deftest test-transfer
  (let [state (fresh-state)
        a1    (mock-create-account! state {:name "Alice" :type "checking"})
        a2    (mock-create-account! state {:name "Bob" :type "checking"})
        _     (mock-deposit! state {:account-id (:id a1) :amount 500.0})]
    (with-redefs [transaction/transfer! (fn [_ data] (mock-transfer! state data))]
      (let [handler (routes/app-routes nil)
            resp    (handler (json-request :post "/api/transactions/transfer"
                                           {:from-account-id (str (:id a1))
                                            :to-account-id   (str (:id a2))
                                            :amount          200.0}))]
        (is (= 201 (:status resp)))
        (is (= 300.0 (:balance (mock-get-account state (:id a1)))))
        (is (= 200.0 (:balance (mock-get-account state (:id a2)))))))))

(deftest test-list-transactions
  (let [state (fresh-state)
        acct  (mock-create-account! state {:name "Test" :type "checking"})
        _     (mock-deposit! state {:account-id (:id acct) :amount 100.0})
        _     (mock-withdraw! state {:account-id (:id acct) :amount 50.0})]
    (with-redefs [transaction/list-transactions (fn [_ id] (mock-list-transactions state id))]
      (let [handler (routes/app-routes nil)
            resp    (handler (mock/request :get (str "/api/transactions/" (:id acct))))]
        (is (= 200 (:status resp)))
        (is (= 2 (count (parse-body resp))))))))

(deftest test-invalid-uuid
  (with-redefs [account/get-account (fn [_ _] nil)]
    (let [handler (routes/app-routes nil)
          resp    (handler (mock/request :get "/api/accounts/not-a-uuid"))]
      (is (= 400 (:status resp))))))

(deftest test-deposit-nonexistent-account
  (with-redefs [transaction/deposit!
                (fn [_ _] (throw (ex-info "Account not found" {:status 404})))]
    (let [handler (routes/app-routes nil)
          resp    (handler (json-request :post "/api/transactions/deposit"
                                         {:account-id (str (UUID/randomUUID)) :amount 50.0}))]
      (is (= 404 (:status resp))))))

(deftest test-validation-error-missing-name
  (let [handler (routes/app-routes nil)
        resp    (handler (json-request :post "/api/accounts" {:type "checking"}))]
    (is (= 400 (:status resp)))))
