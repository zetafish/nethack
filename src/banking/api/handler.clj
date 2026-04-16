(ns banking.api.handler
  (:require [integrant.core :as ig]
            [compojure.api.sweet :refer [api context GET]]
            [ring.util.http-response :refer [ok]]))

(defmethod ig/init-key :api/handler [_ {:keys [db]}]
  (api
   {:swagger {:ui "/swagger"
              :spec "/swagger.json"
              :data {:info {:title "Banking API"
                            :description "Simple banking application"}}}}
   (context "/api" []
     (GET "/health" []
       (ok {:status "ok"})))))
