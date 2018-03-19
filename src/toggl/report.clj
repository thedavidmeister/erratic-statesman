; https://github.com/toggl/toggl_api_docs/blob/master/reports.md
(ns toggl.report
 (:require
  environ.core
  org.httpkit.client
  cheshire.core
  clojure.walk
  toggl.data
  toggl.api))

(def base-url "https://toggl.com/reports/api/v2/")

(def workspace-id (environ.core/env :toggl-workspace-id))

(defn with-defaults
 [options]
 (let [with-workspace #(assoc-in % [:query-params :workspace_id] workspace-id)]
  (-> options
   toggl.api/with-defaults
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
