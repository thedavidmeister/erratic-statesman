(def project 'thedavidmeister/erratic-statesman)
(def version "0.1.1-SNAPSHOT")

(set-env!
 :source-paths   #{"src"}
 :dependencies
 '[[org.clojure/clojure "1.10.0"]
   [adzerk/boot-test "1.2.0" :scope "test"]

   ; REPL.
   [samestep/boot-refresh "0.1.0"]

   ; Other util libs
   [medley "1.1.0"]

   ; Strings
   [funcool/cuerdas "2.1.0"]

   ; JSON
   [cheshire "5.8.1"]

   ; Environment
   [environ "1.0.2"]

   ; HTTP
   [http-kit "2.4.0-alpha2"]

   ; Time
   [clj-time "0.15.1"]])

(require
 '[samestep.boot-refresh :refer [refresh]]
 '[toggl2jira.core])

(deftask repl-server
  []
  (comp
    (watch)
    (refresh)
    (repl :server true)))

(deftask repl-client
  []
  (repl :client true))

(deftask toggl2jira [] (toggl2jira.core/do-it!))
