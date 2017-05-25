(ns toggl2jira.core
 (:require
  environ.core
  toggl.report
  cuerdas.core
  jira.core
  clj-time.format
  clj-time.core))

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

(defn toggl-times->jira-issues
 [times]
 (into #{}
  (flatten
   (map
    (comp map-project-name :project)
    times))))

(defn format-secs
 [secs]
 (clj-time.core/seconds secs))

(defn toggl-time->simplified-toggl-time [time]
 (let [simplified (select-keys time [:description :start :project :id :dur :end])]
  (merge simplified
   {:dur_formatted (format-secs (:dur simplified))})))

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
  user-logs))

(defn jira-time->url
 [jira-time]
 (let [issue-id (:issueId jira-time)
       issue-key (:key (jira.core/mem-api! (str "issue/" issue-id)))
       id (:id jira-time)]
  (str "https://" jira.core/host "/browse/" issue-key "?focusedWorklogId=" id "#worklog-" id)))

(defn jira-time->date-str
 [jira-time]
 (->> jira-time
  :started
  (clj-time.format/parse-local (:date-time clj-time.format/formatters))
  (clj-time.format/unparse-local (:date clj-time.format/formatters))))

(defn jira-time->toggl-candidates
 [jira-time]
 (let [date (jira-time->date-str jira-time)
       candidates (:data
                   (toggl.report/api!
                    "details"
                    {:query-params {:client_ids client-id
                                    :since date
                                    :until date}}))]
  candidates))

(defn reconcile-jira-times
 [toggl-times jira-times]
 (let [
       toggl-indexed (into {}
                      (map
                       (fn [t] [(:id t) t])
                       toggl-times))
       with-toggl-ids #(merge {:toggl-ids (extract-ids (:comment %))} %)
       jira-times (->> jira-times (map with-toggl-ids))
       reconcilable? (fn [jira-time]
                      (let [ids (extract-ids (:comment jira-time))]
                       (prn ids)))]
  (when-let [dangling-times (seq (filter (comp nil? :toggl-ids) jira-times))]
   (let [next-dangling-time (first dangling-times)]
    (throw
     (Exception.
      (str "Jira time " (jira-time->url next-dangling-time) " missing toggl-id! Toggl candidates: "
       (pr-str
        (map
         toggl-time->simplified-toggl-time
         (jira-time->toggl-candidates next-dangling-time))))))))))

(defn do-it!
 []
 (let [toggl-times (toggl-items)
       jira-issues (toggl-times->jira-issues toggl-times)
       jira-times (jira-times-by-issue jira-issues)]
  (reconcile-jira-times toggl-times jira-times)))
