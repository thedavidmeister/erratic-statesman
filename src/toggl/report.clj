; https://github.com/toggl/toggl_api_docs/blob/master/reports.md
(ns toggl.report
 (:require
  environ.core
  org.httpkit.client
  cheshire.core
  clojure.walk
  toggl.data
  toggl.api
  time.core))

(def base-url "https://toggl.com/reports/api/v2/")

(def workspace-id (memoize (fn [] (first (map :id (toggl.api/workspaces!))))))

(defn with-defaults
 [options]
 (let [with-workspace #(assoc-in % [:query-params :workspace_id] (workspace-id))]
  (-> options
   toggl.api/with-defaults
   with-workspace)))

(defn with-page [options page] (assoc-in options [:query-params :page] page))
(defn with-month-until
 [options]
 (assoc-in options [:query-params :until]
  (time.core/->iso8601
   (clj-time.core/plus
    (time.core/iso8601-> (-> options :query-params :since))
    (clj-time.core/months 1)))))

(defn -api!
 "As per toggl.api/api! but handles pagination and body of report response"
 ([endpoint] (-api! endpoint {}))
 ([endpoint options]
  (assert
   (not (:until options))
   "Until not implemented as a parameter")

  (let [url (str base-url endpoint)
        options (with-defaults options)
        since (-> options :query-params :since)
        sinces (time.core/date-repeat since (clj-time.core/now) (clj-time.core/months 1))]
   (doall
    (flatten
     (map
      (fn [s]
       (loop [page 1
              ret []]
        (let [request (org.httpkit.client/get
                       url
                       (-> options
                        (with-page page)
                        (assoc-in [:query-params :since] (time.core/->iso8601 s))
                        with-month-until))]
         (toggl.api/throw-bad-response! @request)
         (if-let [data (-> @request toggl.api/parse-body :data seq)]
          (recur (inc page) (into ret data))
          ret))))
      sinces))))))
(def api! (memoize -api!))
