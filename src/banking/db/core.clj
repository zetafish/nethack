(ns banking.db.core
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ds-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defmethod ig/init-key :db/postgres [_ {:keys [jdbc-url]}]
  (jdbc/get-datasource (assoc ds-opts :jdbcUrl jdbc-url)))

(defn execute! [ds sql-params]
  (jdbc/execute! ds sql-params ds-opts))

(defn execute-one! [ds sql-params]
  (jdbc/execute-one! ds sql-params ds-opts))
