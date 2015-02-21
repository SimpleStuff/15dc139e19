(ns tango.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [tango.web-socket :as ws]
            [tango.http-server :as http]
            [tango.channels :as channels]
            [tango.messaging :as messaging]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources
;http://www.core-async.info/tutorial/a-minimal-client
;https://github.com/enterlab/rente
;http://stuartsierra.com/2013/12/08/parallel-processing-with-core-async
;https://github.com/danielsz/system
; http://localhost:1337/admin
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn production-system [configuration]
  (let [{:keys [port]} configuration]
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

(defn init []
  (alter-var-root 
   #'system (constantly (production-system {:port 1337}))))

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
        (println (str "Server started on port " port))))))
