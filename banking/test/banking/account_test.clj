(ns banking.account-test
  (:require [clojure.test :refer [deftest is testing]]
            [banking.account :as account]))

(deftest create-account-test
  (testing "creates account with valid request"
    (let [store (account/create-store)
          result (account/create-account store {:name "Alice" :type :checking})]
      (is (:ok result))
      (is (= "Alice" (-> result :ok :name)))
      (is (= :checking (-> result :ok :type)))
      (is (= 0M (-> result :ok :balance)))))

  (testing "rejects invalid request"
    (let [store (account/create-store)
          result (account/create-account store {:name "" :type :checking})]
      (is (:error result)))
    (let [store (account/create-store)
          result (account/create-account store {:name "Bob" :type :invalid})]
      (is (:error result)))))

(deftest get-account-test
  (testing "returns existing account"
    (let [store (account/create-store)
          {:keys [ok]} (account/create-account store {:name "Alice" :type :savings})
          result (account/get-account store (:id ok))]
      (is (= ok (:ok result)))))

  (testing "returns error for missing account"
    (let [store (account/create-store)
          result (account/get-account store "nonexistent")]
      (is (:error result)))))

(deftest list-accounts-test
  (testing "lists accounts excluding balancing account"
    (let [store (account/create-store)]
      (account/create-account store {:name "Alice" :type :checking})
      (account/create-account store {:name "Bob" :type :savings})
      (let [accounts (account/list-accounts store)]
        (is (= 2 (count accounts)))
        (is (every? #(not= account/balancing-account-id (:id %)) accounts))))))

(deftest delete-account-test
  (testing "deletes account with zero balance"
    (let [store (account/create-store)
          {:keys [ok]} (account/create-account store {:name "Alice" :type :checking})
          result (account/delete-account store (:id ok))]
      (is (:ok result))
      (is (empty? (account/list-accounts store)))))

  (testing "rejects deleting balancing account"
    (let [store (account/create-store)
          result (account/delete-account store account/balancing-account-id)]
      (is (-> result :error :forbidden))))

  (testing "rejects deleting nonexistent account"
    (let [store (account/create-store)
          result (account/delete-account store "nonexistent")]
      (is (-> result :error :not-found))))

  (testing "rejects deleting account with balance"
    (let [store (account/create-store)
          {:keys [ok]} (account/create-account store {:name "Alice" :type :checking})]
      (account/update-balance store (:id ok) #(+ % 100M))
      (let [result (account/delete-account store (:id ok))]
        (is (-> result :error :forbidden))))))

(deftest update-balance-test
  (testing "updates balance"
    (let [store (account/create-store)
          {:keys [ok]} (account/create-account store {:name "Alice" :type :checking})
          result (account/update-balance store (:id ok) #(+ % 50M))]
      (is (= 50M (-> result :ok :balance)))))

  (testing "rejects negative balance for regular accounts"
    (let [store (account/create-store)
          {:keys [ok]} (account/create-account store {:name "Alice" :type :checking})
          result (account/update-balance store (:id ok) #(- % 10M))]
      (is (-> result :error :insufficient-funds))))

  (testing "allows negative balance for balancing account"
    (let [store (account/create-store)
          result (account/update-balance store account/balancing-account-id #(- % 100M))]
      (is (= -100M (-> result :ok :balance))))))
