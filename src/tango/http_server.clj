(ns tango.http-server
  (:require [org.httpkit.server :as http-kit-server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :as defaults]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;; Post commands
;; Post Query
;; WS/SSE Updates

;; Command example - a two tuple of action and data
;;[:create-customer {:first_name "Rolf"}]

;; Query - a datomic datalog query
;;[:find (pull ?e *) :where ....]

;; Update - a WS message about updated data
;[:tx/accepted {:key #uuid "23434"
;               :value {:system "local-kafka"}
;               :topic "accepted-txes"}]

;; TODO - better UUID generation
;; http://codingstruggles.com/clojure-integrating-friend-with-sente/
;; curl -X POST -v -d "t=d" http://localhost:1337/commands

(defn handler [ajax-post-fn ajax-get-or-ws-handshake-fn]
  (routes
   (GET "/" req {:body (slurp (clojure.java.io/resource "public/index.html"))
                 :session {:uid (rand-int 100)}
                 :headers {"Content-Type" "text/html"}})
   ;(POST "/commands" req (str "Command: " req))
   (POST "/commands" params (fn [req] (str "Command: " req)))
   (GET "/adjudicator" req {:body (slurp (clojure.java.io/resource "public/adjudicator.html"))
                            :session {:uid (rand-int 100)}
                            :headers {"Content-Type" "text/html"}})
   ;; Sente channel routes
   (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
   (POST "/chsk" req (ajax-post-fn req))
   (route/resources "/") ; Static files
   (route/not-found "<h1>Page not found</h1>")))

(defn ring-handlers [ws-connection]
  (:ring-handlers ws-connection))

;; TODO - handle that port is nil
(defrecord HttpServer [port ws-connection server-stop]
  component/Lifecycle
  (start [component]
    (if server-stop
      (do
        (log/info "Server already started")
        component)
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} (ring-handlers ws-connection)

            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn)

            ;; TODO - implement proper CSRF instead of turning it off
            server-stop (http-kit-server/run-server
                         (defaults/wrap-defaults
                           handler
                           (assoc defaults/site-defaults :security {:anti-forgery false})) {:port port})]
        (log/report "HTTP server started")
        (assoc component :server-stop server-stop))))
  (stop [component]
    (when server-stop
      (server-stop)
      (log/report "HTTP server stopped"))
    (assoc component :server-stop nil)))

(defn create-http-server [port]
  (map->HttpServer {:port port}))
