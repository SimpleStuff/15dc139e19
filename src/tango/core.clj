(ns tango.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [org.httpkit.server :as http-kit-server]
            [clojure.core.match :refer [match]]
            [tango.import :as imp]
            [ring.middleware.defaults :as defaults]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources
;http://www.core-async.info/tutorial/a-minimal-client
;https://github.com/enterlab/rente

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
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(defn event-msg-handler* [messages-receive-chan {:as ev-msg :keys [id ?data event ring-req]}]
  (>!! messages-receive-chan {:topic id :payload ?data :sender (:uid (:session ring-req))}))

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_] (stop-f)))

(defn start-router! [messages-receive-chan]
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk (partial event-msg-handler* messages-receive-chan))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Http routing
(defn render [t]
  (apply str t))

(defroutes application-routes
  (GET "/" req {:body (slurp (clojure.java.io/resource "public/main.html"))
                     :session {:uid (rand-int 100)}
                     :headers {"Content-Type" "text/html"}})
  ;; Sente channel routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (route/resources "/") ; Static files
  (route/not-found "<h1>Page not found</h1>"))

(def ring-handler
  (defaults/wrap-defaults application-routes defaults/site-defaults))

(defonce http-server (atom nil))

(defn start-http-server! []
  (reset! http-server (http-kit-server/run-server (var ring-handler) {:port 1337})))

(defn stop-http-server! []
  (when-let [stop-f @http-server]
    (stop-f :timeout 100)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce messages-receive-chan (atom nil))
(defonce messages-send-chan (atom nil))

;; test
;; fix message dispatch call
(defn start-message-loop [messages-receive-chan]
  (go-loop []
    (when-let [msg (<! messages-receive-chan)]
      (println (str "message " msg))
      (swap! message-log conj msg)
      (>! @messages-send-chan (message-dispatch msg))
      (recur))))

;; test
(defn start-message-send-loop [messages-send-chan]
  (go-loop []
    (when-let [msg (<! messages-send-chan)]
      (println (str "send message " msg))
      (swap! message-send-log conj msg)
      (chsk-send! (:id msg) (:message msg))
      (recur))))

;; TODO
;; - component?
(defn stop! []
  (do
    (close! @messages-receive-chan)
    (close! @messages-send-chan)
    (stop-http-server!)
    (stop-router!)))

(defn start! []
  (do
    (reset! messages-receive-chan (chan))
    (start-message-loop @messages-receive-chan)
    (reset! messages-send-chan (chan))
    (start-message-send-loop @messages-send-chan)
    (start-http-server!)
    (start-router! @messages-receive-chan)))

(defn restart! []
  (stop!)
  (start!))

(defn -main
  [& args]
  (do
    (start!)
    (println "Server Started")))
