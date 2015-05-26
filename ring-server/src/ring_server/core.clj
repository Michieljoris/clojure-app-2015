(ns ring-server.core 
  (:require 
   [ring.middleware.reload :as reload]
   [org.httpkit.server :refer [run-server with-channel on-close websocket? on-receive send!]]
   [bidi.ring :refer [make-handler]]
   [bidi.bidi :refer [match-route path-for]]
   ))

(def route ["/index.html" :index])
(match-route route "/index.html")

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "foobarhello HTTP!"})

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

(def routes (make-handler ["/" {"" app
                                "ws" ws-handler}]))

(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (println "Starting server..")
  (let [handler (reload/wrap-reload #'routes)]
    (run-server handler {:port 8080})))
