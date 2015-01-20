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
                     alts! alts!! timeout]]
            ))

(def memory-log (atom []))

(defn logf [s]
  (swap! memory-log conj s))

(defn import-file [{:keys [content]}]
  (imp/dance-perfect-xml->data (imp/read-xml-string content)))

(def message-chan (chan))
(def message-send-chan (chan))

;; (go (println (<! test-chan)))
;; (>!! test-chan "Duudde")

(def message-log (atom []))
(def message-send-log (atom []))

(go (while true
      (let [msg (<! message-chan)]
        (swap! message-log conj msg)
        (message-dispatch msg message-send-chan))))

(go (while true
      (let [msg (<! message-send-chan)]
        (swap! message-send-log conj msg)
        (chsk-send! (:id msg) (:message msg)))))


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

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event ring-req]}]
  (do
    ;(logf (str "Event: " event))
    (println (str "Id: " (:uid (:session ring-req))))
    ;(println (str "Request : " ring-req))
    ;(message-dispatch id ?data (:uid (:session ring-req)))
    (>!! message-chan {:topic id :payload ?data :sender (:uid (:session ring-req))})))

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

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

(defn stop! []
  (do
    (stop-http-server!)
    (stop-router!)))

(defn start! []
  (do
    (start-http-server!)
    (start-router!)))

(defn -main
  [& args]
  (do
    (start!)
    (println "Server Started")))
