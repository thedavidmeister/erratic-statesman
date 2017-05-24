(ns toggl2jira.core
 (:require
  environ.core
  toggl.report
  cuerdas.core
  jira.core))

(def client-id (environ.core/env :toggl2jira-client-id))

(defn extract-ids
 [s]
 (let [regex-match #(re-find #"\[.+\]" %)
       drop-brackets #(cuerdas.core/trim % "[]")
       expand-csvs #(map cuerdas.core/trim (clojure.string/split % #","))]
  (some-> s
   regex-match
   drop-brackets
   expand-csvs)))

; Toggl data processing.

(defn map-project-name
 [toggl-project-name]
 (if-let [ids (extract-ids toggl-project-name)]
   ids
   (throw (Exception. (str "Bad project name: " toggl-project-name)))))

(defn no-empty-projects
 "Throw an error if we are missing a project for a time entry"
 [items]
 (let [nil-project-items (get
                          (group-by :project items)
                          nil)]
  (if nil-project-items
   (throw (Exception. (str "Missing project for items: " (pr-str nil-project-items))))
   items)))

; Jira data processing.

(defn toggl-items
 []
 (let [api-response (toggl.report/api! "details" {:query-params {:client_ids client-id
                                                                 :since "2017-01-01"}})
       ; https://github.com/toggl/toggl_api_docs/issues/262
       filter-projects #(remove (comp nil? :project) %)]
  (->> api-response
   :data
   no-empty-projects)))

(defn jira-times-by-issue
 [projects]
 (let [issue-logs (flatten
                   (pmap
                    (comp
                     :worklogs
                     jira.core/api!
                     #(str "issue/" % "/worklog"))
                    projects))
       user-logs (filter
                  #(-> % :author :key (= jira.core/user))
                  issue-logs)]

  issue-logs))

(defn reconcile-jira-times
 [toggl-times jira-times]
 (let [toggl-indexed (into {}
                      (map
                       (fn [t] [(:id t) t])
                       toggl-times))
       reconcilable? (fn [jira-time]
                      (let [ids (extract-ids (:comment jira-time))]))]
  (remove reconcilable? jira-times)))

(defn do-it!
 []
 (let [toggl-times (toggl-items)
       jira-issues (keys
                    (into #{}
                     (map
                      (comp #(into #{} %) map-project-name :project)
                      toggl-times)))
       jira-times (jira-times-by-issue jira-issues)]
  (reconcile-jira-times toggl-times jira-times)))
