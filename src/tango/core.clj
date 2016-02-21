;; ## Tango System composition
;; `tango.core` is the main entry point for the application and is
;; responsible for managing the life-cycle of all the other system
;; components.
(ns tango.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]

            [tango.broker :as broker]
            [tango.web-socket :as ws]
            [tango.http-server :as http]
            [tango.channels :as channels]
            [tango.import-engine :as import]
            [tango.event-access :as access]
            [tango.event-file-storage :as file-storage]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Resources
;;

;; https://github.com/stathissideris/dali

;; - http://www.core-async.info/tutorial/a-minimal-client
;; - https://github.com/enterlab/rente
;; - http://stuartsierra.com/2013/12/08/parallel-processing-with-core-async
;; - https://github.com/danielsz/system
;; - http://localhost:1337/admin
;; - https://github.com/weavejester/codox
;; - http://gdeer81.github.io/marginalia/
;; - https://github.com/gdeer81/marginalia/blob/master/src/marginalia/core.clj
;; - https://en.wikipedia.org/wiki/Markdown
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn production-system
  "Creates a system configured for production use.
   
   Configuration map can contain the following :
  
    {:port
     :log-file
     :log-level}

   `:port` - integer that specifies the port number to start the http server on.

   `:log-file` - string that specifies the path for log file output.

   `:log-level` - Log level can be any of:

   - `:debug`
   - `:info` 
   - `:trace`
   - `:error`
   - `:warn`
   - `:fatal`
  "
  [configuration]
  (log/report "Creating system")
  (let [{:keys [port log-file log-level id-generator-fn client-connection]} configuration
        id-gen-fn (or id-generator-fn #(java.util.UUID/randomUUID))]
   
    (log/merge-config! {:level log-level

                        :appenders {:println {:min-level :error}

                                    :spit (appenders/spit-appender {:fname log-file})}})

    (component/system-map

     ;; Import handling
     :file-handler-channels (import/create-file-handler-channels)
     :file-handler (component/using (import/create-file-handler id-gen-fn) [:file-handler-channels])

     ;; Client channels
     :channel-connection-channels (channels/create-channel-connection-channels)
     :channel-connection (component/using (channels/create-channel-connection) [:channel-connection-channels])

     ;; Http/WS-Service
     :ws-connection-channels (ws/create-ws-channels)
     :ws-connection (component/using (ws/create-ws-connection) [:ws-connection-channels])
     :http-server (component/using (http/create-http-server port) [:ws-connection])

     ;; Event Storage
     :event-file-storage-channels (file-storage/create-event-file-storage-channels)
     :event-file-storage (component/using (file-storage/create-event-file-storage "./file-storage.dat")
                                     [:event-file-storage-channels])

     ;; Event Access
     :event-access-channels (access/create-event-access-channels)
     :event-access (component/using (access/create-event-access)
                                    {:event-access-channels :event-access-channels
                                     :storage-channels :event-file-storage-channels})

     ;; Message broker
     :message-broker (component/using (broker/create-message-broker)
                                      {:channel-connection-channels client-connection
                                       :file-handler-channels :file-handler-channels
                                       :event-access-channels :event-access-channels}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defines a pointer to the current system.
(def system nil)

(defn init
  "Initializes a Production system with default values."
  []
  (alter-var-root 
   #'system (constantly (production-system {:port 1337 :log-file "loggs/test.log" :log-level :info
                                            :client-connection :ws-connection-channels}))))

(defn start
  "Recursivly starts all components in the system"
  []
  (alter-var-root #'system component/start))

(defn stop
  "Recursivly stops all components in the system"
  []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go
  "Entry point from a REPL, will initilize a default system and start it."
  []
  (init)
  (start))

(defn reset
  "REPL helper that allow to restart the application and reload namespaces."
  []
  (stop)
  (refresh :after 'tango.core/go))

;; TODO - Add possibillity to set log level as a param
;; #{:trace :debug :info :warn :error :fatal :report}
(defn -main 
  "Main entry point for the application. Valid args is an integer value that
  specify port number for the http-server to start on."
  [& args]
  (let [[port] args]
    (if-not port
      (println "Port number missing")
      (do
        (component/start
         (production-system {:port 1337 :log-file "loggs/production.log" :log-level :info
                             :client-connection :ws-connection-channels}))
        (log/report (str "Server started on port " port))))))
