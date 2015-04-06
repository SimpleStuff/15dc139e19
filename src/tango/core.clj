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
;; Resources
;http://www.core-async.info/tutorial/a-minimal-client
;https://github.com/enterlab/rente
;http://stuartsierra.com/2013/12/08/parallel-processing-with-core-async
;https://github.com/danielsz/system
; http://localhost:1337/admin
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn production-system [configuration]
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
;; Application

(def system nil)

;; TODO - log-level must be fixed with tests, not tests spew loggs
(defn init []
  (alter-var-root 
   #'system (constantly (production-system {:port 1337 :log-file "loggs/test.log" :log-level :debug}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'tango.core/go))

(defn -main [& args]
  (let [[port] args]
    (if-not port
      (println "Port number missing")
      (do
        (component/start (production-system {:port (Integer/parseInt port)}))
        (log/info (str "Server started on port " port))))))
