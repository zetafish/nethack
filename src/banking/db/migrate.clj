(ns banking.db.migrate
  (:require [integrant.core :as ig]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]))

(defn- migration-config [ds]
  {:datastore (ragtime-jdbc/sql-database {:datasource ds})
   :migrations (ragtime-jdbc/load-resources "migrations")})

(defmethod ig/init-key :db/migrate [_ {:keys [db]}]
  (ragtime-repl/migrate (migration-config db))
  nil)

(defn -main [& _]
  (let [system (requiring-resolve 'banking.system/read-config)
        config (system)
        ds-config (:db/postgres config)
        ds ((requiring-resolve 'next.jdbc/get-datasource) {:jdbcUrl (:jdbc-url ds-config)})]
    (ragtime-repl/migrate (migration-config ds))))
