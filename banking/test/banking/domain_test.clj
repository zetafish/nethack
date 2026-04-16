(ns banking.domain-test
  (:require [clojure.test :refer [deftest is testing]]
            [banking.domain :as domain]))

(deftest validate-create-account-test
  (testing "valid account"
    (is (nil? (domain/validate domain/CreateAccount {:name "Alice"})))
    (is (nil? (domain/validate domain/CreateAccount {:name "Bob" :account-type "savings"}))))

  (testing "invalid - empty name"
    (is (some? (domain/validate domain/CreateAccount {:name ""}))))

  (testing "invalid - bad account type"
    (is (some? (domain/validate domain/CreateAccount {:name "Alice" :account-type "crypto"})))))

(deftest validate-deposit-test
  (let [uuid (java.util.UUID/randomUUID)]
    (testing "valid deposit"
      (is (nil? (domain/validate domain/Deposit {:account-id uuid :amount 100.0}))))

    (testing "invalid - zero amount"
      (is (some? (domain/validate domain/Deposit {:account-id uuid :amount 0.0}))))

    (testing "invalid - negative amount"
      (is (some? (domain/validate domain/Deposit {:account-id uuid :amount -50.0}))))))

(deftest validate-transfer-test
  (let [from (java.util.UUID/randomUUID)
        to   (java.util.UUID/randomUUID)]
    (testing "valid transfer"
      (is (nil? (domain/validate domain/Transfer
                                 {:from-account-id from :to-account-id to :amount 25.0}))))

    (testing "invalid - missing to"
      (is (some? (domain/validate domain/Transfer
                                  {:from-account-id from :amount 25.0}))))))
