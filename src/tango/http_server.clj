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
            [om.next.server :as om]
            [tango.datomic-storage :as d]
            [tango.export2 :as exp]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate (fn [env key params] key))

(defmethod mutate 'app/status
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info "Mutate status")
             (str "Mutate status"))})

(defmethod mutate 'app/online?
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info (async/>!! state {:topic :command :sender :http :payload [key (:remote params)]}))
             (str "Mutate online"))})

(defmethod mutate 'app/select-activity
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info "Select activity")
             (async/>!! state {:topic :command :sender :http :payload [key params]}))})

(defmethod mutate 'participant/set-result
  [{:keys [state] :as env} key params]
  {:action (fn []
             (async/>!! state {:topic :command :sender :http :payload [key params]})
             (log/info (str "Set result " key " " params)))})

(defmethod mutate 'app/set-export-status
  [{:keys [state] :as env} key params]
  {:action (fn []
             (log/info "Export " params)
             (when (= (:required (:status params)))
               ; (ds/get-selected-activity conn ['*])
               ;; X TODO - adjudicator/number must be added in import
               ;; X TODO - round dances
               ;; X TODO - participant/number
               ;; TODO - round motsvarande "S"
               (let [conn (d/create-connection "datomic:free://localhost:4334//competitions")
                     selected-acts (d/get-selected-activites conn [:activity/name
                                                                   :activity/id
                                                                   {:round/panel
                                                                    [{:adjudicator-panel/adjudicators [:adjudicator/number]}]}
                                                                   {:round/dances [:dance/name]}])
                     results (d/query-all-results conn [:result/mark-x
                                                        {:result/adjudicator ['*]}
                                                        {:result/participant [:participant/number]}])]
                 (log/info "Export Started")
                 (log/info "Selected Acts " selected-acts)
                 (log/info "Results " results)
                 (exp/export-results selected-acts results))))})

(defmethod mutate 'app/log
  [{:keys [state] :as env} key params]
  {:action (fn []
             (let [msg (:message params)
                   log-msg (str "Client Log : " msg)]
               (condp = (:level params)
                 :debug (log/debug log-msg)
                 :trace (log/trace log-msg)
                 :error (log/error log-msg)
                 :info (log/info log-msg))))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read
(defmulti reader (fn [env key params] key))

;; TODO - clients should send query params instead of filtering on the client
(defmethod reader :app/selected-activity
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "Selector in selected activity" query))
            ;(d/get-selected-activity state query)
            (d/get-selected-activites state query)
            )
   ;(do (log/info (str "Reader Query Key Params " query key params)))
   })

;(let [selected-act (d/get-selected-activity state '[:activity/id])]
;  (log/info (str "Selected act " selected-act))
;  (when selected-act
;    (d/query-results state query (:activity/id selected-act))))

(defmethod reader :app/results
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/results read"))
            (d/query-all-results state query))})

(defmethod reader :app/selected-competition
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/selected-competition read"))
            "Selected comp")})

;; TODO - hack fix
(defmethod reader :app/selected-adjudicator
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/results read"))
            nil)})

(defmethod reader :app/heat-page
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/heat-page read"))
            nil)})

(defmethod reader :app/heat-page-size
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/page read"))
            nil)})

(defmethod reader :app/admin-mode
  [{:keys [state query]} key params]
  {:value (do
            (log/info (str "app/admin-mode read"))
            nil)})

(def parser
  (om/parser {:mutate mutate
              :read reader}))

(defn handle-command [ch-out req]
  (do
    (parser {:state ch-out} (:command (:params req)))
    ;(log/info "Command params " (:command (:params req)))
    {:body req})
  ;(str (:remote (:params req)))
  ;(str (prn (t/read (t/reader (:body req) :json))))
  ;(prn (t/read (t/reader (:body req) :json)))
  )

(defn handle-query [ch-out datomic-storage-uri req]
  (let [conn (d/create-connection datomic-storage-uri)
        ;result (d/get-selected-activity conn)
        result (parser {:state conn} (clojure.edn/read-string (:query (:params req))))
        ]
    ;(async/>!! ch-out {:topic :query
    ;                   :sender :http
    ;                   :payload (clojure.edn/read-string
    ;                              (:query (:params req)))})

    ;(parser {:state conn} (clojure.edn/read-string (:query (:params req))))
    (log/info (str "Request Query " req))
    (log/info (str "Query >> " result))
    {:body {:query result}})
  )

(defn handler [ajax-post-fn ajax-get-or-ws-handshake-fn http-server-channels datomic-storage-uri]
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
   (GET "/query" req (partial handle-query (:out-channel http-server-channels) datomic-storage-uri))
   ;; Sente channel routes
   (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
   (POST "/chsk" req (ajax-post-fn req))
   (route/resources "/") ; Static files
   (route/not-found "<h1>Page not found</h1>")))

(defn ring-handlers [ws-connection]
  (:ring-handlers ws-connection))

;; TODO - handle that port is nil
(defrecord HttpServer [port http-server-channels ws-connection server-stop datomic-storage-uri]
  component/Lifecycle
  (start [component]
    (if server-stop
      (do
        (log/info "Server already started")
        component)
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} (ring-handlers ws-connection)
            handler (handler ajax-post-fn ajax-get-or-ws-handshake-fn http-server-channels datomic-storage-uri)

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

(defn create-http-server [port datomic-storage-uri]
  (map->HttpServer {:port port :datomic-storage-uri datomic-storage-uri}))

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
