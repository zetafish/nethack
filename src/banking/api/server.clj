(ns banking.api.server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [clojure.tools.logging :as log]))

(defmethod ig/init-key :api/server [_ {:keys [handler port]}]
  (log/info "Starting server on port" port)
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/halt-key! :api/server [_ server]
  (log/info "Stopping server")
  (.stop server))
