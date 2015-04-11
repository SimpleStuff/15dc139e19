;; ## Tango System composition
;; `tango.core` is the main entry point for the application and is
;; responsible for managing the life-cycle of all the other system
;; components.
(ns tango.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [tango.web-socket :as ws]
            [tango.http-server :as http]
            [tango.channels :as channels]
            [tango.messaging :as messaging]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Resources
;;
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
  (let [{:keys [port log-file log-level]} configuration]
    (when log-file
      (log/info "Enable file logging to " log-file)
      (log/set-config! [:Appenders :spit :enabled?] true)
      (log/set-config! [:shared-appender-config :spit-filename] log-file))
    (if log-level
      (log/set-level! log-level))
    (component/system-map
     :channels (channels/create-channels)
     :ws-connection
     (component/using (ws/create-ws-connection) [:channels])
     :http-server
     (component/using (http/create-http-server port) [:ws-connection])
     :message-handler
     (component/using (messaging/create-message-handler) [:ws-connection :channels]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defines a pointer to the current system.
(def system nil)

(defn init
  "Initializes a Production system with default values."
  []
  (alter-var-root 
   #'system (constantly (production-system {:port 1337 :log-file "loggs/test.log" :log-level :debug}))))

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

(defn -main 
  "Main entry point for the application. Valid args is an integer value that
  specify port number for the http-server to start on."
  [& args]
  (let [[port] args]
    (if-not port
      (println "Port number missing")
      (do
        (component/start (production-system {:port (Integer/parseInt port)}))
        (log/info (str "Server started on port " port))))))
