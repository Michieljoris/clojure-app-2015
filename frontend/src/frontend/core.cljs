(ns frontend.core
  (:require 
   ;; standard and piggieback:
   [clojure.browser.repl :as repl] 
   ;; weasel:
   ;; [weasel.repl :as ws-repl]
   )
  )

(.log js/console "Hey bla sup?!")

;; standard and piggyback:
(repl/connect "http://localhost:8090/repl")

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
