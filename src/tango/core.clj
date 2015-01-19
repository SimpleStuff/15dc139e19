(ns tango.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [org.httpkit.server :as http-kit-server]
            [clojure.core.match :refer [match]]
            [tango.import :as imp]
            [ring.middleware.defaults :as defaults]))

(def memory-log (atom []))

(defn logf [s]
  (swap! memory-log conj s))

(defn import-file [{:keys [content]}]
  ;; (println "-----------------")
  ;(println (str "import " content))
  ;(println (str (imp/dance-perfect-xml->data (imp/read-xml-string content)))
  ;(println "-----------------")
  (imp/dance-perfect-xml->data (imp/read-xml-string content))
  )

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event ring-req]}]
  (do
    ;(logf (str "Event: " event))
    (println (str "Id: " (:uid (:session ring-req))))
    ;(println (str "Request : " ring-req))
    (match [id ?data]
           [:file/import xml]
           (chsk-send! (:uid (:session ring-req)) [:file/imported {:content (import-file xml)}])
           [:client/ping p] (chsk-send! 1 [:server/pong {:content 1}])
           :else (println (str "Event : " event)))))

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
(defn render [t]
  (apply str t))

(defroutes application-routes
  (GET "/" req "<h1>Tango on!</h1>")
  (GET "/admin" req {:body (slurp (clojure.java.io/resource "public/main.html"))
                     :session {:uid 1}
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
