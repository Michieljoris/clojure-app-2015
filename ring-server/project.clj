(defproject ring-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-devel "1.3.2"]
                 [ring-cors "0.1.7"]

                 ;; [ring/ring-defaults        "0.1.3"] ; Includes `ring-anti-forgery`, etc.

                 ;; https://github.com/weavejester/environ
                 [environ "1.0.0"]
                 [http-kit "2.1.17"] 
                 [bidi "1.19.0"]
                 [com.taoensso/sente "1.4.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [hiccup "1.0.5"] ; Optional, just for HTML
                 [com.taoensso/timbre "3.4.0"] 


                 [com.datomic/datomic-free "0.9.4755"]
                 ;; [com.datomic/datomic-pro "0.9.5067"]

                 ;; [com.cognitect/transit-clj  "0.8.259"]

                 ]


  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]
                                  [cider/cider-nrepl "0.9.0-SNAPSHOT"] ;; or add to ~/.lein/profiles.clj
                                  ]

                   :repl-options { ;; Specify the string to print when prompting for input.
                                  ;; defaults to something like (fn [ns] (str *ns* "=> "))
                                  ;; :prompt (fn [ns] (str "your command for <" ns ">, master? " ))
                                  ;; What to print when the repl session starts.
                                  ;; :welcome (println "Welcome to the magical world of the repl!")
                                  ;; Specify the ns to start the REPL in (overrides :main in
                                  ;; this case only)
                                  ;; :init-ns foo.bar
                                  ;; This expression will run when first opening a REPL, in the
                                  ;; namespace from :init-ns or :main if specified.
                                  :init (println "Here we are in" *ns*)
                                  ;; Print stack traces on exceptions (highly recommended, but
                                  ;; currently overwrites *1, *2, etc).
                                  ;; :caught clj-stacktrace.repl/pst+
                                  ;; Skip's the default requires and printed help message.
                                  :skip-default-init false
                                  ;; Customize the socket the repl task listens on and
                                  ;; attaches to.
                                  :host "127.0.0.1"
                                  :port 15123
                                  ;;for more options see the sample project.clj
                   ;; :uberjar {:aot :all}
                   }
                   }
             }

  :main ring-server.core
  )
