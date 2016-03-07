(ns tango.http-server
  (:require [org.httpkit.server :as http-kit-server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.transit :refer [wrap-transit-params]]
            [taoensso.timbre :as log]
            [cognitect.transit :as t]
            [om.next.server :as om]))

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

(defmulti mutate (fn [env key params] key))

(defmethod mutate 'app/status
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info "Mutate status")
             (str "Mutate status"))})

(defmethod mutate 'app/online?
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info (async/>!! state {:topic :command :sender :http :payload key}))
             (str "Mutate online"))})

(def parser
  (om/parser {:mutate mutate}))

(defn handle-command [ch-out req]
  (do
    (parser {:state ch-out} (:remote (:params req)))

    {:body "Tjena"})
  ;(str (:remote (:params req)))
  ;(str (prn (t/read (t/reader (:body req) :json))))
  ;(prn (t/read (t/reader (:body req) :json)))
  )

(defn handler [ajax-post-fn ajax-get-or-ws-handshake-fn http-server-channels]
  (routes
   (GET "/" req {:body (slurp (clojure.java.io/resource "public/index.html"))
                 :session {:uid (rand-int 100)}
                 :headers {"Content-Type" "text/html"}})
   ;(POST "/commands" req (str "Command: " req))
   (POST "/commands" params (partial handle-command (:out-channel http-server-channels))        ;(fn [req] (str "Command: " req))
     )
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
(defrecord HttpServer [port http-server-channels ws-connection server-stop]
  component/Lifecycle
  (start [component]
    (if server-stop
      (do
        (log/info "Server already started")
        component)
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} (ring-handlers ws-connection)

            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn http-server-channels)

            ;; TODO - implement proper CSRF instead of turning it off
            server-stop (http-kit-server/run-server
                          (wrap-transit-params
                            (defaults/wrap-defaults
                              handler
                              (assoc defaults/site-defaults :security {:anti-forgery false}))) {:port port})]
        (log/report "HTTP server started")
        (assoc component :server-stop server-stop))))
  (stop [component]
    (when server-stop
      (server-stop)
      (log/report "HTTP server stopped"))
    (assoc component :server-stop nil)))

(defn create-http-server [port]
  (map->HttpServer {:port port}))

(defrecord HttpServerChannels [in-channel out-channel]
  component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting HttpServer Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing HttpServer Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-http-channels []
  (map->HttpServerChannels {}))
