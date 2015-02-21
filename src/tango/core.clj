(ns tango.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [org.httpkit.server :as http-kit-server]
            [clojure.core.match :refer [match]]
            [tango.import :as imp]
            [ring.middleware.defaults :as defaults]
            [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources
;http://www.core-async.info/tutorial/a-minimal-client
;https://github.com/enterlab/rente
;http://stuartsierra.com/2013/12/08/parallel-processing-with-core-async
;https://github.com/danielsz/system
; http://localhost:1337/admin
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def memory-log (atom []))
(def message-log (atom []))
(def message-send-log (atom []))

(defn logf [s]
  (swap! memory-log conj s))

;; TODO - move calls to imp.
(defn import-file-stream [{:keys [content]}]
  (imp/dance-perfect-xml->data (imp/read-xml-string content)))


(defn message-dispatch [handler-map {:keys [topic payload sender]} ]
  (match [topic payload]
         [:file/import xml]
         {:id sender :message [:file/imported {:content ((:file-import handler-map) xml)}]}
         [:client/ping p]
         {:id sender :message [:server/pong []]}
         :else {:id sender :message [:client/unkown-topic {:topic topic}]}))

(defn create-message-dispatch [handler-map]
  (partial message-dispatch handler-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente socket setup


(defn event-msg-handler* [messages-receive-chan {:as ev-msg :keys [id ?data event ring-req]}]
  (>!! messages-receive-chan {:topic id :payload ?data :sender (:uid (:session ring-req))}))

(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers channels]
  component/Lifecycle
  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component
      (let [;component (component/stop component)
            
            {:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket!)]
        (println "Start WS")
        (assoc component
          :ch-recv ch-recv
          :connected-uids connected-uids
          :send-fn send-fn
          :stop-the-thing 
          (sente/start-chsk-router! ch-recv (partial event-msg-handler* (:messages-receive-chan channels)))
          :ring-handlers (->WSRingHandlers ajax-post-fn ajax-get-or-ws-handshake-fn)))))
  (stop [component]
    (println "Stop WS")
    ;(when ch-recv (close! ch-recv))
    (:stop-the-thing component)
    (assoc component
      :ch-recv nil :connected-uids nil :send-fn nil :ring-handlers nil)))

(defn create-ws-connection []
  (map->WSConnection {}))

(defn send-socket! [ws-connection user-id event]
  ((:send-fn ws-connection) user-id event))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Http routing
(defn render [t]
  (apply str t))

(defn handler [ajax-post-fn ajax-get-or-ws-handshake-fn]
  (routes
   (GET "/" req {:body (slurp (clojure.java.io/resource "public/main.html"))
                     :session {:uid (rand-int 100)}
                     :headers {"Content-Type" "text/html"}})
   ;; Sente channel routes
   (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
   (POST "/chsk" req (ajax-post-fn req))
   (route/resources "/") ; Static files
   (route/not-found "<h1>Page not found</h1>")))

(defn ring-handlers [ws-connection]
  (:ring-handlers ws-connection))

(defrecord HttpServer [port ws-connection server-stop]
  component/Lifecycle
  (start [component]
    (if server-stop
      (do (println "Server already started")
          component)
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (ring-handlers ws-connection)

            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn)

            server-stop (http-kit-server/run-server
                         (defaults/wrap-defaults handler defaults/site-defaults) {:port port})]
        (println "HTTP server started")
        (assoc component :server-stop server-stop))))
  (stop [component]
    (when server-stop
      (do (server-stop)
          (println "HTTP server stopped")))
    (assoc component :server-stop nil)))

(defn create-http-server [port]
  (map->HttpServer {:port port}))

(defn start-message-loop [dispatch-fn messages-receive-ch messages-send-ch system-ch]
  (go-loop []
    (when-let [msg (<! messages-receive-ch)]
      (try
        (if msg
          (do
            (println (str "Message " msg))
            (>! messages-send-ch (dispatch-fn msg))))
        (catch Exception e
          (>! system-ch (str "Exception message: " (.getMessage e)))))
      (recur))))

(defn start-message-send-loop [send-fn messages-send-chan system-ch]
  (go-loop []
    (when-let [msg (<! messages-send-chan)]
      (try
        (if msg
          (do
            (println (str "Sending " msg))
            (send-fn msg)))
        (catch Exception e
          (>! system-ch (str "Exception message: " (.getMessage e)))))
      (recur))))

(defrecord MessageHandler [dispatch-fn ws-connection channels database]
  component/Lifecycle
  (start [component]
    (println "Starting Message Handler")
    (let [msg-send (start-message-send-loop (fn [msg] (send-socket! ws-connection (:id msg) (:message msg)))
                                            (:messages-send-chan channels) (:system-chan channels))
          msg-rec (start-message-loop
                   (partial message-dispatch {:file-import #(import-file-stream %)})
                   (:messages-receive-chan channels) (:messages-send-chan channels) (:system-chan channels))]
      (assoc component :message-sender msg-send :message-receiver msg-rec)))

  (stop [component]
    (println "Stopping Message Handling")
    (assoc component :message-sender nil :message-receiver nil)))

(defn create-message-handler []
  (map->MessageHandler {}))

(defrecord Channels [messages-receive-chan messages-send-chan system-chan]
  component/Lifecycle
  (start [component]
    (if (and messages-receive-chan messages-send-chan system-chan)
      component
      (let [;component (component/stop component)
            ]
        (println "Open Channels")
        (assoc component
          :messages-receive-chan (chan)
          :messages-send-chan (chan)
          :system-chan (chan)))))
  (stop [component]
    (println "Closing Channels")
    (if-let [rec-ch (:messages-receive-chan component)]
      (close! rec-ch))
    (if-let [send-ch (:messages-send-chan component)]
      (close! send-ch))
    (if-let [sys-ch (:system-chan component)]
      (close! sys-ch))
    (assoc component
        :messages-receive-chan nil
        :messages-send-chan nil
        :system-chan nil)))

(defn create-channels []
  (map->Channels {}))

(defn production-system []
  (component/system-map
   :channels (create-channels)
   :ws-connection
   (component/using (create-ws-connection) [:channels])
   :http-server
   (component/using (create-http-server 1337) [:ws-connection])
   :message-handler (component/using (create-message-handler) [:ws-connection :channels])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

(def system nil)

(defn init []
  (alter-var-root 
   #'system (constantly (production-system))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go! []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'tango.core/go!))

(defn -main
  [& args]
  (do
    (go!)
    (println "Server Started")))
