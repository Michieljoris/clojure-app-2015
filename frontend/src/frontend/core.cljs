(ns frontend.core
  (:require-macros
   [hiccups.core :as hiccups]
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
   )
  (:require 
   ;; standard and piggieback:
   ;; [clojure.browser.repl :as repl] 
   ;; weasel:
   ;; [weasel.repl :as ws-repl]

   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente  :as sente :refer (cb-success?)]
   [rum]
   [datascript :as d]

   [hiccups.runtime :as hiccupsrt]


   [taoensso.encore :as enc    :refer (tracef debugf infof warnf errorf)]
   [clojure.string     :as str]
   [cljs.pprint :refer (pprint)]
   )
  )


;; (def schema {:aka {:db/cardinality :db.cardinality/many}})
;; (def conn   (d/create-conn ))
;; (def r  (d/transact! conn [
;;                            { :db/id 104
;;                              :name  "Bijou"
;;                             }

;;                            { :db/id 103
;;                              :name  "Bijou"
;;                             }
;;                            ]))
;; (pprint (first r))
;; (d/q '[ :find  ?e ?name ?tx 
;;        :where [?e :name ?name ?tx ]
;;         ]
;;      @conn)


;; (let [schema {:aka {:db/cardinality :db.cardinality/many}}
;;       conn   (d/create-conn schema)]
;;   (d/transact! conn [ { :db/id -1
;;                         :name  "Maksim"
;;                         :age   45
;;                         :aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ])
;;   (d/q '[ :find  ?n ?a
;;           :where [?e :aka "Maks Otto von Stirlitz"]
;;                  [?e :name ?n]
;;                  [?e :age  ?a] ]
;;        @conn))

(defn set-html
  "Sets `.innerHTML` of the given tagert element to the give `html`"
  [target & html]
  (set! target.innerHTML (apply str html)))

(defn set-text
  "Sets `.textContent` of the given `tagret`  to the given `text`"
  [target & text]
  (set! target.textContent (apply str text)))

(.log js/console "Hi Bijou?!")

;; standard and piggyback:
;; (repl/connect "http://localhost:8090/repl")


(def chsk-url-fn
  "(ƒ [path window-location websocket?]) -> server-side chsk route URL string.
    * path       - As provided to client-side `make-channel-socket!` fn
                   (usu. \"/chsk\").
    * websocket? - True for WebSocket connections, false for Ajax (long-polling)
                   connections.
    * window-location - Map with keys:
      :href     ; \"http://www.example.org:80/foo/bar?q=baz#bang\"
      :protocol ; \"http:\" ; Note the :
      :hostname ; \"example.org\"
      :host     ; \"example.org:80\"
      :pathname ; \"/foo/bar\"
      :search   ; \"?q=baz\"
      :hash     ; \"#bang\"
  Note that the *same* URL is used for: WebSockets, POSTs, GETs. Server-side
  routes should be configured accordingly."
  (fn [path {:as window-location :keys [protocol host pathname]} websocket?]
    (str (if-not websocket? protocol (if (= protocol "https:") "wss:" "ws:"))
         "//localhost:8080"  (or path pathname))))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
        :chsk-url-fn chsk-url-fn
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )



;;weasel:
;; (if-not (ws-repl/alive?)
;;   (ws-repl/connect "ws://127.0.0.1:8092"))

;; (enable-console-print!)

;; (println "You can change this line an see the changes in the dev console")

;; (defn foo [a b]
;;   (+ a b))

;; (defn foo2 [a b]
;;   (+ a b))

;; (. js/console (log "And now it works!!!" (foo 1 2)))
;; (println "hell")(f)

;; (defonce state (atom {:text "hello2asdfasdf"}))

;; (println (str "the state is" @state))

(hiccups/defhtml body []
 [:h1 "Sente reference example"]
 [:p "An Ajax/WebSocket connection has been configured (random)."]
 [:hr]
 [:p [:strong "Step 1: "] "Open browser's JavaScript console."]
 [:p [:strong "Step 2: "] "Try: "
  [:button#btn1 {:type "button"} "chsk-send! (w/o reply)"]
  [:button#btn2 {:type "button"} "chsk-send! (with reply)"]]
 ;;
 [:p [:strong "Step 3: "] "See browser's console + nREPL's std-out." ]
 ;;
 [:hr]
 [:h2 "Login with a user-id"]
 [:p  "The server can use this id to send events to *you* specifically."]
 [:p [:input#input-login {:type :text :placeholder "User-id"}]
  [:button#btn-login {:type "button"} "Secure login!"]]
 ;; [:script {:src "main.js"}] ; Include our cljs target
 )


(hiccups/defhtml my-template []
  [:div
   [:a {:href "https://github.com/weavejester/hiccup"}
    "Hiccup"]])

;; Ineject "Hello world!" into document body.
;; (set-html document.body
;;           "<div style='background: black; color: white;'>"
;;           "<p>Hello world!</p>"
;;           "</div>")

;; (set-html document.body (my-template))
(set-html document.body (body))

(rum/defc label [n text]
  [:.label (repeat n text)])

;; (rum/mount (label 1  "Hi Bijou! ") (.-body js/document))

;;;; Routing handlers

;; So you'll want to define one server-side and one client-side
;; (fn event-msg-handler [ev-msg]) to correctly handle incoming events. How you
;; actually do this is entirely up to you. In this example we use a multimethod
;; that dispatches to a method based on the `event-msg`'s event-id. Some
;; alternatives include a simple `case`/`cond`/`condp` against event-ids, or
;; `core.match` against events.

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  ;; (debugf "Event: %s" event)
  (event-msg-handler ev-msg))

(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (debugf "Unhandled event: %s" event))

  (defmethod event-msg-handler :chsk/state
    [{:as ev-msg :keys [?data]}]
    ;; (if (= ?data {:first-open? true})
    ;;   (debugf "Channel socket successfully established!")
    ;;   (debugf "Channel socket state change: %s" ?data))
    )

  (defmethod event-msg-handler :chsk/recv
    [{:as ev-msg :keys [?data]}]
    (debugf "Push event from server: %s" ?data))

  (defmethod event-msg-handler :chsk/handshake
    [{:as ev-msg :keys [?data]}]
    (let [[?uid ?csrf-token ?handshake-data] ?data]
      ;; (debugf "Handshake: %s" ?data)
      ))

  ;; Add your (defmethod handle-event-msg! <event-id> [ev-msg] <body>)s here...
  )

(def router_ (atom nil))

(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defn start! []
  (start-router!)
  )
;; (start!)

;;;; Client-side UI

(when-let [target-el (.getElementById js/document "btn1")]
  (.addEventListener target-el "click"
    (fn [ev]
      (debugf "Button 1 was clicked (won't receive any reply from server)")
      (chsk-send! [:example/button1 {:had-a-callback? "nope"}])
      )
    ))

(when-let [target-el (.getElementById js/document "btn2")]
  (.addEventListener target-el "click"
    (fn [ev]
      (debugf "Button 2 was clicked (will receive reply from server)")
      (chsk-send! [:example/button2 {:had-a-callback? "indeed"}] 5000
        (fn [cb-reply] (debugf "Callback reply: %s" cb-reply))))))


(when-let [target-el (.getElementById js/document "btn-login")]
  (.addEventListener target-el "click"
    (fn [ev]
      (let [user-id (.-value (.getElementById js/document "input-login"))]
        (if (str/blank? user-id)
          (js/alert "Please enter a user-id first")
          (do
            (debugf "Logging in with user-id %s" user-id)

            ;;; Use any login procedure you'd like. Here we'll trigger an Ajax
            ;;; POST request that resets our server-side session. Then we ask
            ;;; our channel socket to reconnect, thereby picking up the new
            ;;; session.

            (sente/ajax-call "http://localhost:8080/login"
              {:method :post
               :params {:user-id    (str user-id)
                        :csrf-token (:csrf-token @chsk-state)}}
              (fn [ajax-resp]
                (debugf "Ajax login response: %s" ajax-resp)
                (let [login-successful? true ; Your logic here
                      ]
                  (if-not login-successful?
                    (debugf "Login failed")
                    (do
                      (debugf "Login successful")
                      (sente/chsk-reconnect! chsk))))))
            ))))))
