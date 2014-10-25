(ns tango.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [org.httpkit.server :as http-kit-server]))

(def memory-log (atom []))

(defn logf [s]
  (swap! memory-log conj s))

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (logf "Event: %s" event))

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

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Http routing

(defroutes application-routes
  (GET "/" [] "<h1>Tango on!</h1>")
    ;; Sente channel routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (route/resources "/") ; Static files
  (route/not-found "<h1>Page not found</h1>"))

(defonce http-server (atom nil))

(defn start-http-server! []
  (reset! http-server (http-kit-server/run-server (var application-routes) {:port 1337})))

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
