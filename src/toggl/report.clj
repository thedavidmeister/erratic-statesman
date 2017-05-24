; https://github.com/toggl/toggl_api_docs/blob/master/reports.md
(ns toggl.report
 (:require
  environ.core
  org.httpkit.client
  cheshire.core))

(def base-url "https://toggl.com/reports/api/v2/")

(def token (environ.core/env :toggl-token))
; The name of your application or your email address so we can get in touch in
; case you're doing something wrong.
(def user-agent (environ.core/env :toggl-user-agent))

(def workspace-id (environ.core/env :toggl-workspace-id))

(defn with-defaults
 [options]
 (let [with-token #(merge {:basic-auth [token "api_token"]} %)
       with-agent #(assoc-in % [:query-params :user_agent] user-agent)
       with-workspace #(assoc-in % [:query-params :workspace_id] workspace-id)]
  (-> options
   with-token
   with-agent
   with-workspace)))

(defn api!
 ([endpoint] (api! endpoint {}))
 ([endpoint options]
  (let [url (str base-url endpoint)
        request (org.httpkit.client/get
                 url
                 (with-defaults options))]
   (assert (= 200 (:status @request)))
   (-> @request
    :body
    cheshire.core/parse-string))))
