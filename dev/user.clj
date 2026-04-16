(ns user
  (:require [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [banking.system :as system]))

(integrant.repl/set-prep! system/read-config)
