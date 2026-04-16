(ns banking.transaction-test
  (:require [clojure.test :refer [deftest is testing]]
            [banking.account :as account]
            [banking.transaction :as tx]))

(defn- setup []
  (let [store (account/create-store)
        ledger (tx/create-ledger)
        alice (-> (account/create-account store {:name "Alice" :type :checking}) :ok)
        bob (-> (account/create-account store {:name "Bob" :type :savings}) :ok)]
    {:store store :ledger ledger :alice alice :bob bob}))

(deftest deposit-test
  (testing "deposits into account and debits balancing account"
    (let [{:keys [store ledger alice]} (setup)
          result (tx/deposit store ledger {:account-id (:id alice) :amount 100M})]
      (is (:ok result))
      (is (= :deposit (-> result :ok :type)))
      (is (= 100M (-> result :ok :amount)))
      (is (= 2 (count (-> result :ok :entries))))
      (is (= 100M (-> (account/get-account store (:id alice)) :ok :balance)))
      (is (= -100M (-> (account/get-account store account/balancing-account-id) :ok :balance)))))

  (testing "rejects deposit with invalid amount"
    (let [{:keys [store ledger alice]} (setup)
          result (tx/deposit store ledger {:account-id (:id alice) :amount -5M})]
      (is (:error result))))

  (testing "rejects deposit to nonexistent account"
    (let [{:keys [store ledger]} (setup)
          result (tx/deposit store ledger {:account-id "nope" :amount 100M})]
      (is (:error result)))))

(deftest withdraw-test
  (testing "withdraws from account with sufficient funds"
    (let [{:keys [store ledger alice]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 200M})
      (let [result (tx/withdraw store ledger {:account-id (:id alice) :amount 50M})]
        (is (:ok result))
        (is (= :withdrawal (-> result :ok :type)))
        (is (= 150M (-> (account/get-account store (:id alice)) :ok :balance))))))

  (testing "rejects withdrawal with insufficient funds"
    (let [{:keys [store ledger alice]} (setup)
          result (tx/withdraw store ledger {:account-id (:id alice) :amount 50M})]
      (is (-> result :error :insufficient-funds)))))

(deftest transfer-test
  (testing "transfers between accounts"
    (let [{:keys [store ledger alice bob]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 500M})
      (let [result (tx/transfer store ledger {:from-account-id (:id alice)
                                              :to-account-id (:id bob)
                                              :amount 200M})]
        (is (:ok result))
        (is (= :transfer (-> result :ok :type)))
        (is (= 300M (-> (account/get-account store (:id alice)) :ok :balance)))
        (is (= 200M (-> (account/get-account store (:id bob)) :ok :balance))))))

  (testing "rejects transfer with insufficient funds"
    (let [{:keys [store ledger alice bob]} (setup)
          result (tx/transfer store ledger {:from-account-id (:id alice)
                                            :to-account-id (:id bob)
                                            :amount 100M})]
      (is (-> result :error :insufficient-funds))))

  (testing "rejects transfer to same account"
    (let [{:keys [store ledger alice]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 100M})
      (let [result (tx/transfer store ledger {:from-account-id (:id alice)
                                              :to-account-id (:id alice)
                                              :amount 50M})]
        (is (-> result :error :validation)))))

  (testing "rejects transfer to nonexistent account (rollback)"
    (let [{:keys [store ledger alice]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 100M})
      (let [result (tx/transfer store ledger {:from-account-id (:id alice)
                                              :to-account-id "nonexistent"
                                              :amount 50M})]
        (is (:error result))
        ;; Balance should be rolled back
        (is (= 100M (-> (account/get-account store (:id alice)) :ok :balance)))))))

(deftest history-test
  (testing "returns transactions for specific account"
    (let [{:keys [store ledger alice bob]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 100M})
      (tx/deposit store ledger {:account-id (:id bob) :amount 50M})
      (tx/transfer store ledger {:from-account-id (:id alice)
                                 :to-account-id (:id bob)
                                 :amount 25M})
      (let [alice-history (tx/history ledger (:id alice))
            bob-history (tx/history ledger (:id bob))]
        (is (= 2 (count alice-history)))
        (is (= 2 (count bob-history))))))

  (testing "returns all transactions"
    (let [{:keys [store ledger alice bob]} (setup)]
      (tx/deposit store ledger {:account-id (:id alice) :amount 100M})
      (tx/withdraw store ledger {:account-id (:id alice) :amount 20M})
      (tx/transfer store ledger {:from-account-id (:id alice)
                                 :to-account-id (:id bob)
                                 :amount 30M})
      (is (= 3 (count (tx/all-transactions ledger)))))))
