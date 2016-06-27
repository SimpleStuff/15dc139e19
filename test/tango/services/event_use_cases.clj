(ns tango.services.event-use-cases
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.event-manager :as manager]
            [tango.event-access :as access]
            [clojure.core.async :as async]
            [tango.datomic-storage :as d]
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
    (let [;event-manager-channels (u/create-test-channels 1000)
          ;; TODO - should be command to clear storage
          _ (d/delete-storage mem-uri)
          event-manager-channels {:in-channel  (async/timeout 786)
                                  :out-channel (async/timeout 564)}

          event-system (component/system-map
                         :event-access-channels (access/create-event-access-channels)
                         :event-access (component/using
                                         (access/create-event-access
                                           mem-uri
                                           schema-path)
                                         {:event-access-channels :event-access-channels})
                         ;; TODO - use real channels
                         :event-manager-channels event-manager-channels
                         :event-manager (component/using (manager/create-event-manager)
                                                         {:event-manager-channels :event-manager-channels
                                                          :event-access-channels  :event-access-channels}))

          _ (component/start event-system)
          _ (async/go (async/>! (:in-channel event-manager-channels)
                                {:topic   :event-manager/create-competition
                                 :payload {:competition/id   #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                                           :competition/name "Test Competition"}}))
          create-competition-reply (first (async/alts!! [(:out-channel event-manager-channels)
                                                         (async/timeout 1000)]))

          _ (async/go (async/>! (:in-channel event-manager-channels)
                                {:topic   :event-manager/create-class
                                 :payload {:competition/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                                           :competition/class
                                                           {:class/name "Test Class"
                                                          :class/id   #uuid "666dcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}}}))
          create-class-reply (first (async/alts!! [(:out-channel event-manager-channels)
                                                   (async/timeout 1000)]))

          _ (async/go (async/>! (:in-channel event-manager-channels)
                                {:topic   :event-manager/query-competition
                                 :payload {:query [:competition/name]}}))
          query-competition-reply (first (async/alts!! [(:out-channel event-manager-channels)
                                                        (async/timeout 1000)]))]
      (is (= create-competition-reply
             {:topic   :event-manager/tx-processed
              :payload {:topic   :event-manager/create-competition}}))

      (is (= create-class-reply
             {:topic   :event-manager/tx-processed
              :payload {:topic :event-manager/create-class}}))

      (is (= query-competition-reply
             {:topic   :event-manager/tx-processed
              :payload {:result [{:competition/name "Test Competition"}]
                        :topic  :event-manager/query-competition}}))
      (component/stop event-system))))
