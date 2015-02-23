(ns tango.http-server
  (:require [org.httpkit.server :as http-kit-server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :as defaults]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;; TODO - better UUID generation
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
      (do
        (log/info "Server already started")
        component)
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (ring-handlers ws-connection)

            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn)

            server-stop (http-kit-server/run-server
                         (defaults/wrap-defaults handler defaults/site-defaults) {:port port})]
        (log/info "HTTP server started")
        (assoc component :server-stop server-stop))))
  (stop [component]
    (when server-stop
      (do (server-stop)
          (log/info "HTTP server stopped")))
    (assoc component :server-stop nil)))

(defn create-http-server [port]
  (map->HttpServer {:port port}))
