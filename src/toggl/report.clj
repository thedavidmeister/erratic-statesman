; https://github.com/toggl/toggl_api_docs/blob/master/reports.md
(ns toggl.report
 (:require
  environ.core
  org.httpkit.client
  cheshire.core
  clojure.walk))

(def base-url "https://toggl.com/reports/api/v2/")

(def token (environ.core/env :toggl-token))
; The name of your application or your email address so we can get in touch in
; case you're doing something wrong.
(def user-agent (environ.core/env :toggl-user-agent))

(def workspace-id (environ.core/env :toggl-workspace-id))

(defn with-defaults
 [options]
 (let [with-auth #(merge {:basic-auth [token "api_token"]} %)
       with-agent #(assoc-in % [:query-params :user_agent] user-agent)
       with-workspace #(assoc-in % [:query-params :workspace_id] workspace-id)]
  (-> options
   with-auth
   with-agent
   with-workspace)))

(defn with-page [options page] (assoc-in options [:query-params :page] page))

(defn api!
 ([endpoint] (api! endpoint {}))
 ([endpoint options]
  (let [url (str base-url endpoint)
        options (with-defaults options)]
   (loop [page 1
          ret []]
    (let [request (org.httpkit.client/get
                   url
                   (with-page options page))]
     (when-not (= 200 (:status @request))
      (throw (Exception. (:body @request))))

     (if-let [data (-> @request :body cheshire.core/parse-string clojure.walk/keywordize-keys :data seq)]
      (recur (inc page) (into ret data))
      ret))))))
