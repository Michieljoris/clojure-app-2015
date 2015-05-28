(ns frontend.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require 
   ;; standard and piggieback:
   [clojure.browser.repl :as repl] 
   ;; weasel:
   ;; [weasel.repl :as ws-repl]

   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente  :as sente :refer (cb-success?)]
   )
  )

(.log js/console "Hey bla sup?!")

;; standard and piggyback:
;; (repl/connect "http://localhost:8090/repl")

;; (ns my-client-side-ns ; .cljs
;;   (:require-macros
;;    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
;;   (:require
;;    ;; <other stuff>
;;    [cljs.core.async :as async :refer (<! >! put! chan)]
;;    [taoensso.sente  :as sente :refer (cb-success?)] ; <--- Add this
;;   ))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
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
