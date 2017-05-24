(ns jira.core
 (:require
  clojure.walk))

(def base-url (str "https://" (environ.core/env :jira-host ) "/rest/api/latest/"))
(def user (environ.core/env :jira-user))
(def password (environ.core/env :jira-password))

(defn with-defaults
 [options]
 (let [with-auth #(merge {:basic-auth [user password]} %)]
  (-> options
   with-auth)))

(defn api!
 ([endpoint] (api! endpoint {}))
 ([endpoint options]
  (let [url (str base-url endpoint)
        request (org.httpkit.client/get
                 url
                 (-> options
                  with-defaults))]
   (if (= 200 (:status @request))
    (let [parsed (-> @request
                  :body
                  cheshire.core/parse-string
                  clojure.walk/keywordize-keys)]
     (if (= (:total parsed) (:maxResults parsed))
      parsed
      (throw (Exception. (str "Time to implement pagination! " parsed)))))
    (throw (Exception. (:body @request)))))))
