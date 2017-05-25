(def project 'thedavidmeister/erratic-statesman)
(def version "0.1.0-SNAPSHOT")

(set-env!
 :source-paths   #{"src"}
 :dependencies
 '[[org.clojure/clojure "1.9.0-alpha15"]
   [adzerk/boot-test "1.1.1" :scope "test"]

   ; REPL.
   [samestep/boot-refresh "0.1.0"]

   ; Other util libs
   [medley "1.0.0"]

   ; Strings
   [funcool/cuerdas "2.0.3"]

   ; JSON
   [cheshire "5.7.1"]

   ; Environment
   [environ "1.1.0"]

   ; HTTP
   [http-kit "2.2.0"]

   ; Time
   [clj-time "0.13.0"]])

(require
 '[samestep.boot-refresh :refer [refresh]])

(deftask repl-server
  []
  (comp
    (watch)
    (refresh)
    (repl :server true)))

(deftask repl-client
  []
  (repl :client true))
