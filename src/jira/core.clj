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
                 (with-defaults options))]
   (if (= 200 (:status @request))
    (-> @request
     :body
     cheshire.core/parse-string
     clojure.walk/keywordize-keys)
    (throw (Exception. (:body @request)))))))
