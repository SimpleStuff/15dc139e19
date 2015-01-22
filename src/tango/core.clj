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

(defn logf [s]
  (swap! memory-log conj s))

(defn import-file [{:keys [content]}]
  (imp/dance-perfect-xml->data (imp/read-xml-string content)))

(def message-log (atom []))
(def message-send-log (atom []))

(defn message-dispatch [{:keys [topic payload sender]} out-chan]
  (match [topic payload]
         [:file/import xml]
         (>!! out-chan {:id sender :message [:file/imported {:content (import-file xml)}]}) 
         [:client/ping p]
         (>!! out-chan {:id sender :message [:server/pong {:content 1}]})
         :else (println (str "Unmatched message topicz: " topic))))

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
  (GET "/" req "<h1>Tango on!</h1>")
  (GET "/admin" req {:body (slurp (clojure.java.io/resource "public/main.html"))
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
(defonce messages-send-chan (atom (chan)))

(defn start-message-loop [messages-receive-chan]
  (go-loop []
    (when-let [msg (<! messages-receive-chan)]
      (println (str "message " msg))
      (swap! message-log conj msg)
      (message-dispatch msg @messages-send-chan)
      (recur))))

(go (while true
      (let [msg (<! @messages-send-chan)]
        (swap! message-send-log conj msg)
        (chsk-send! (:id msg) (:message msg)))))

(defn stop! []
  (do
    (close! @messages-receive-chan)
    (stop-http-server!)
    (stop-router!)))

(defn start! []
  (do
    (reset! messages-receive-chan (chan))
    (start-message-loop @messages-receive-chan)
    (start-http-server!)
    (start-router! @messages-receive-chan)))

(defn -main
  [& args]
  (do
    (start!)
    (println "Server Started")))
