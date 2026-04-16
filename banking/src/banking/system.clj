(ns banking.system
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [banking.api.routes :as routes])
  (:import [com.zaxxer.hikari HikariDataSource]))

(def config
  {:db/postgres   {:jdbc-url (or (System/getenv "DATABASE_URL")
                                 "jdbc:postgresql://localhost:5432/banking?user=banking&password=banking")}
   :http/server   {:port (Integer/parseInt (or (System/getenv "PORT") "3000"))
                   :db   (ig/ref :db/postgres)}})

(defmethod ig/init-key :db/postgres [_ {:keys [jdbc-url]}]
  (let [ds (connection/->pool HikariDataSource
                              {:jdbcUrl jdbc-url
                               :maximumPoolSize 10})]
    ds))

(defmethod ig/halt-key! :db/postgres [_ ds]
  (.close ^HikariDataSource ds))

(defmethod ig/init-key :http/server [_ {:keys [port db]}]
  (let [handler (-> (routes/app-routes db)
                    routes/wrap-exception-handler)]
    (println (str "Starting server on port " port))
    (jetty/run-jetty handler {:port port :join? false})))

(defmethod ig/halt-key! :http/server [_ server]
  (.stop server))

(defn start! []
  (ig/init config))

(defn stop! [system]
  (ig/halt! system))
