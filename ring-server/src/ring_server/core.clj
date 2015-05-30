(ns ring-server.core 
  (:require 
   ;;database
   [datomic.api :as d]

   ;;server
   [org.httpkit.server :refer [run-server with-channel on-close websocket? on-receive send!]]
   [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]

   ;;ring middleware
   ;; [ring.middleware.defaults]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.cors :refer [wrap-cors]]

   ;;routing
   [bidi.ring :refer [make-handler]]
   [bidi.bidi :refer [match-route path-for]]

   ;;websocket
   [taoensso.sente :as sente] 
   
   ;;other
   [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop)]
   ;; [taoensso.sente.packers.transit :as sente-transit]
   [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)])
   ;; [clojure.pprint :refer [pprint]]

  ;; (:gen-class))
  )

;; (def packer (sente-transit/get-flexi-packer :edn)) ; Experimental, needs Transit dep
(def packer :edn) ; Default packer (no need for Transit dep)

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:packer packer})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   ;; :body    "foobarfoobarhello HTTP!"})
   :body    "ring server"})


(defn login!
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-request]
  (let [{:keys [session params]} ring-request
        {:keys [user-id]} params]
    (debugf "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

(def routes ["/" {
                  "" app
                  ;;add more api routes here..
                  "login" {:post {"" login!}}
                  "chsk" {:get { "" ring-ajax-get-or-ws-handshake }
                          :post { ""  ring-ajax-post }}
                  }]
  )

(def handler (make-handler routes))

(defn cors-wrapper [handler]
  (wrap-cors handler
             :access-control-allow-origin [#"http://localhost:3449"]
             :access-control-allow-methods [:get :put :post :delete])
  )

(def ring-handler
  (-> #'handler
      wrap-reload
      wrap-keyword-params
      wrap-params
      cors-wrapper
      ))

;;Sente
(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  ;; (debugf "Event: %s" event)
  (event-msg-handler ev-msg))


(do ; Server-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (debugf "Unhandled event: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

  (defmethod event-msg-handler :chsk/ws-ping 
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      ;; (debugf "Ping event: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

  (defmethod event-msg-handler :example/button1
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (debugf "Button1 foobar: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

  ;; Add your (defmethod event-msg-handler <event-id> [ev-msg] <body>)s here...
  )

(defonce router_ (atom nil))

(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (println "Starting sente router...")
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defonce web-server_ (atom nil)) ; {:server _ :port _ :stop-fn (fn [])}
(defn stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))

(defn start-web-server!* [ring-handler port]
  (println "Starting http-kit...")
  (let [http-kit-stop-fn (run-server ring-handler {:port port})]
    {:server  nil ; http-kit doesn't expose this
     :port    (:local-port (meta http-kit-stop-fn))
     :stop-fn (fn [] (http-kit-stop-fn :timeout 100))}))

(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [{:keys [stop-fn port] :as server-map}
        (start-web-server!* ring-handler
          (or port 0) ; 0 => auto (any available) port
          )
        uri (format "http://localhost:%s/" port)]
    (debugf "Web server is running at `%s`" uri)
    ;; (try
    ;;   (.browse (java.awt.Desktop/getDesktop) (java.net.URI. uri))
    ;;   (catch java.awt.HeadlessException _))
    (reset! web-server_ server-map)))


(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (start-router!)
  (start-web-server! 8080)
  )
;; (-main)

(defn test-fast-server>user-pushes []
  (doseq [uid (:any @connected-uids)]
    (doseq [i (range 100)]
      (chsk-send! uid [:fast-push/is-fast (str "hello " i "!!")]))))

(comment (test-fast-server>user-pushes))

(defn start-broadcaster! []
  (go-loop [i 0]
    (<! (async/timeout 10000))
    (println (format "Broadcasting server>user: %s" @connected-uids))
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
        [:some/broadcast
         {:what-is-this "A broadcast pushed from server"
          :how-often    "Every 10 seconds"
          :to-whom uid
          :i i}]))
    (recur (inc i))))

(comment (start-broadcaster!))


;; (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
;;   (POST "/chsk" req (ring-ajax-post                req))
;; ["/" {"blog" {:get {"/index" (fn [req] {:status 200 :body "Index"})}}}]

;; ["/" {"blog" {:get
;;               {"/index" (fn [req] {:status 200 :body "Index"})}}
;;       {:request-method :post :server-name "juxt.pro"}
;;       {"/zip" (fn [req] {:status 201 :body "Created"})}}]




;; (defn login!
;;   "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
;;   In our simplified example we'll just always successfully authenticate the user
;;   with whatever user-id they provided in the auth request."
;;   [ring-request]
;;   (let [{:keys [session params]} ring-request
;;         {:keys [user-id]} params]
;;     (debugf "Login request: %s" params)
;;     {:status 200 :session (assoc session :uid user-id)}))


;; (defroutes my-routes
;;   (GET  "/"      req (landing-pg-handler req))
;;   ;;
;;   (GET  "/chsk"  req (ring-ajax-get-or-ws-handshake req))
;;   (POST "/chsk"  req (ring-ajax-post                req))
;;   (POST "/login" req (login! req))
;;   ;;
;;   (route/resources "/") ; Static files, notably public/main.js (our cljs target)
;;   (route/not-found "<h1>Page not found</h1>"))


;; (def my-ring-handler
;;   (let [ring-defaults-config
;;         (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
;;           {:read-token (fn [req] (-> req :params :csrf-token))})]

;;     ;; NB: Sente requires the Ring `wrap-params` + `wrap-keyword-params`
;;     ;; middleware to work. These are included with
;;     ;; `ring.middleware.defaults/wrap-defaults` - but you'll need to ensure
;;     ;; that they're included yourself if you're not using `wrap-defaults`.
;;     ;;
;;     (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)))


;;;; Routing handlers

;; So you'll want to define one server-side and one client-side
;; (fn event-msg-handler [ev-msg]) to correctly handle incoming events. How you
;; actually do this is entirely up to you. In this example we use a multimethod
;; that dispatches to a method based on the `event-msg`'s event-id. Some
;; alternatives include a simple `case`/`cond`/`condp` against event-ids, or
;; `core.match` against events.


;; ;;;; Example: broadcast server>user

;; ;; As an example of push notifications, we'll setup a server loop to broadcast
;; ;; an event to _all_ possible user-ids every 10 seconds:


;;  ; Note that this'll be fast+reliable even over Ajax!:
;; (defn test-fast-server>user-pushes []
;;   (doseq [uid (:any @connected-uids)]
;;     (doseq [i (range 100)]
;;       (chsk-send! uid [:fast-push/is-fast (str "hello " i "!!")]))))

;; (comment (test-fast-server>user-pushes))

;; ;;;; Init

;;  (defonce web-server_ (atom nil)) ; {:server _ :port _ :stop-fn (fn [])}
;;  (defn stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))

;; (defn start-web-server! [& [port]]
;;   (stop-web-server!)
;;   (let [{:keys [stop-fn port] :as server-map}
;;         (start-web-server!* (var my-ring-handler)
;;           (or port 0) ; 0 => auto (any available) port
;;           )
;;         uri (format "http://localhost:%s/" port)]
;;     (debugf "Web server is running at `%s`" uri)
;;     (try
;;       (.browse (java.awt.Desktop/getDesktop) (java.net.URI. uri))
;;       (catch java.awt.HeadlessException _))
;;     (reset! web-server_ server-map)))

;; (defn start! []
;;   (start-router!)
;;   (start-web-server!)
;;   (start-broadcaster!))

;; ;; (start!) ; Server-side auto-start disabled for LightTable, etc.
;; (comment (start!)
;;          (test-fast-server>user-pushes))


;; (defn ws-handler [req]
;;   (with-channel req channel              ; get the channel
;;     ;; communicate with client using method defined above
;;     (on-close channel (fn [status]
;;                         (println "channel closed")))
;;     (if (websocket? channel)
;;       (println "WebSocket channel")
;;       (println "HTTP channel")
;;       ;; (send! channel (app req))
;;       )
;;     (on-receive channel (fn [data]       ; data received from client
;;            ;; An optional param can pass to send!: close-after-send?
;;            ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
;;            ;; and false for WebSocket.  (send! channel data close-after-send?)
;;                           (send! channel data))))) 

;; (def myurl
;;   "(ƒ [path window-location websocket?]) -> server-side chsk route URL string.
;;     * path       - As provided to client-side `make-channel-socket!` fn
;;                    (usu. \"/chsk\").
;;     * websocket? - True for WebSocket connections, false for Ajax (long-polling)
;;                    connections.
;;     * window-location - Map with keys:
;;       :href     ; \"http://www.example.org:80/foo/bar?q=baz#bang\"
;;       :protocol ; \"http:\" ; Note the :
;;       :hostname ; \"example.org\"
;;       :host     ; \"example.org:80\"
;;       :pathname ; \"/foo/bar\"
;;       :search   ; \"?q=baz\"
;;       :hash     ; \"#bang\"
;;   Note that the *same* URL is used for: WebSockets, POSTs, GETs. Server-side
;;   routes should be configured accordingly."
;;   (fn [path {:as window-location :keys [protocol host pathname]} websocket?]
;;     (str (if-not websocket? protocol (if (= protocol "https:") "wss:" "ws:"))
;;      "//" host (or path pathname))))

;; (myurl "/mypath" { :protocol "https:" :host "localhost:8080"} true)


;; (def chsk-url-fn
;;   "(ƒ [path window-location websocket?]) -> server-side chsk route URL string.
;;     * path       - As provided to client-side `make-channel-socket!` fn
;;                    (usu. \"/chsk\").
;;     * websocket? - True for WebSocket connections, false for Ajax (long-polling)
;;                    connections.
;;     * window-location - Map with keys:
;;       :href     ; \"http://www.example.org:80/foo/bar?q=baz#bang\"
;;       :protocol ; \"http:\" ; Note the :
;;       :hostname ; \"example.org\"
;;       :host     ; \"example.org:80\"
;;       :pathname ; \"/foo/bar\"
;;       :search   ; \"?q=baz\"
;;       :hash     ; \"#bang\"
;;   Note that the *same* URL is used for: WebSockets, POSTs, GETs. Server-side
;;   routes should be configured accordingly."
;;   (fn [path {:as window-location :keys [protocol host pathname]} websocket?]
;;     (str (if-not websocket? protocol (if (= protocol "https:") "wss:" "ws:"))
;;          "//localhost:8080"  (or path pathname))))

;; (chsk-url-fn "/mypath" { :protocol "https:" :host "localhost:8080"} true)



(def url (str "datomic:free://127.0.0.1:4334/bar3"))


(let [conn (d/connect url)
      db (d/db conn)
      result (d/q

              '[:find ?e ?v
                :where
                [?e :alerts/id ?v]
                ]
              db
              )]
  result
  )




  ;; (d/transact conn [ { 
  ;;                       :name  "Maksim"
  ;;                       :age   45
  ;;                       :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ])
