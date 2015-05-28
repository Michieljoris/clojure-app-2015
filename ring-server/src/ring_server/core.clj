(ns ring-server.core 
  (:require 
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]
   [org.httpkit.server :refer [run-server with-channel on-close websocket? on-receive send!]]
   [bidi.ring :refer [make-handler]]
   [bidi.bidi :refer [match-route path-for]]
   [taoensso.sente :as sente] 
   [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
   )
  ;; (:gen-class))
  )

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(def route ["/index.html" :index])
(match-route route "/index.html")

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "foobarfoobarhello HTTP!"})

(defn ws-handler [req]
  (with-channel req channel              ; get the channel
    ;; communicate with client using method defined above
    (on-close channel (fn [status]
                        (println "channel closed")))
    (if (websocket? channel)
      (println "WebSocket channel")
      (println "HTTP channel")
      ;; (send! channel (app req))
      )
    (on-receive channel (fn [data]       ; data received from client
           ;; An optional param can pass to send!: close-after-send?
           ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
           ;; and false for WebSocket.  (send! channel data close-after-send?)
                          (send! channel data))))) 

(def routes ["/" {"" app
                  "ws" ws-handler
                  "chsk" {:get { "" ring-ajax-get-or-ws-handshake }
                          :post { ""  ring-ajax-post }}
                  }]

  )


;; (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
;;   (POST "/chsk" req (ring-ajax-post                req))
;; ["/" {"blog" {:get {"/index" (fn [req] {:status 200 :body "Index"})}}}]

;; ["/" {"blog" {:get
;;               {"/index" (fn [req] {:status 200 :body "Index"})}}
;;       {:request-method :post :server-name "juxt.pro"}
;;       {"/zip" (fn [req] {:status 201 :body "Created"})}}]


(def handler (make-handler routes))

(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (println "Starting server..")
  (run-server (-> #'handler
                  wrap-reload
                  wrap-keyword-params
                  wrap-params
                  )
                {:port 8080}))
