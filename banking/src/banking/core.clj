(ns banking.core
  (:require [integrant.core :as ig]
            [clojure.java.io :as io]
            [banking.db :as db]
            [banking.api :as api])
  (:import [org.eclipse.jetty.server Server]))

(defmethod ig/init-key :banking/db [_ {:keys [jdbc-url]}]
  (db/create-datasource jdbc-url))

(defmethod ig/halt-key! :banking/db [_ ds]
  (.close ds))

(defmethod ig/init-key :banking/handler [_ {:keys [db]}]
  (api/app db))

(defmethod ig/init-key :banking/server [_ {:keys [handler port]}]
  (let [server (ring.adapter.jetty/run-jetty handler {:port port :join? false})]
    (println (str "Server started on port " port))
    server))

(defmethod ig/halt-key! :banking/server [_ ^Server server]
  (.stop server))

(defn -main [& _]
  (let [config (-> "config.edn" io/resource slurp ig/read-string)]
    (ig/init config)))
