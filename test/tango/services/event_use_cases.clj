(ns tango.services.event-use-cases
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.event-manager :as manager]
            [tango.event-access :as access]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup utils

(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-path "schema/activity.edn")
;(def schema-tx (read-string (slurp "./resources/schema/activity.edn")))

(def test-competition (atom nil))
(def conn (atom nil))

;(deftest create-connection
;  (testing "Create a connection to db"
;    (is (not= nil (ds/create-storage mem-uri ds/select-activity-schema)))
;    (is (not= nil (ds/create-connection mem-uri)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup fixtures

;(defn setup-db [test-fn]
;  (ds/delete-storage mem-uri)
;  (ds/create-storage mem-uri schema-tx)
;  (reset! test-competition (imp/competition-xml->map
;                             u/real-example
;                             #(java.util.UUID/randomUUID)))
;  (reset! conn (ds/create-connection mem-uri))
;  (test-fn))
;
;(use-fixtures :each setup-db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit tests

(deftest classes-can-be-transformed
  (testing "Classes can be created"
    (let [                                                  ;event-manager-channels (u/create-test-channels 1000)
          event-manager-channels {:in-channel (async/timeout 786)
                                  :out-channel (async/timeout 564)}

          event-system (component/system-map
                         :event-access-channels (access/create-event-access-channels)
                         :event-access (component/using
                                                  (access/create-event-access
                                                    mem-uri
                                                    schema-path)
                                                  {:event-access-channels :event-access-channels})
                         :event-manager-channels event-manager-channels
                         :event-manager (component/using (manager/create-event-manager)
                                                         {:event-manager-channels :event-manager-channels
                                                          :event-access-channels :event-access-channels}))
          ]
      (component/start event-system)
      (async/go (async/>! (:in-channel event-manager-channels)
                          {:topic :create-class
                           :payload {:class/name "Test Class"
                                     :class/id "1"}}))
      (is (= (first (async/alts!! [(:out-channel event-manager-channels)
                                   (async/timeout 500)]))
             1))
      (component/stop event-system))))
