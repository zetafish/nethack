(ns banking.system
  (:require [integrant.core :as ig]
            [aero.core :as aero]
            [clojure.java.io :as io]))

(defn read-config []
  (-> (io/resource "config.edn")
      (aero/read-config)))

(defn start []
  (-> (read-config)
      ig/prep
      ig/init))

(defn -main [& _]
  (start))
