(ns toggl2jira.core
 (:require
  environ.core
  toggl.report
  cuerdas.core))

(def client-id (environ.core/env :toggl2jira-client-id))

(defn map-project-name
 [toggl-project-name]
 (let [regex-match (re-find #"\[.+\]" toggl-project-name)]
  (if regex-match
   (cuerdas.core/trim regex-match "[]")
   (throw (Exception. (str "Bad project name: " toggl-project-name))))))

(defn no-empty-projects
 "Throw an error if we are missing a project for a time entry"
 [items]
 (let [nil-project-items (get
                          (group-by :project items)
                          nil)]
  (if nil-project-items
   (throw (Exception. (str "Missing project for items: " (pr-str nil-project-items))))
   items)))


(defn do-it!
 []
 (let [api-response (toggl.report/api! "details" {:query-params {:client_ids client-id
                                                                 :since "2017-01-01"}})
       ; https://github.com/toggl/toggl_api_docs/issues/262
       filter-projects #(remove (comp nil? :project) %)
       group-by-jira-project #(group-by (comp map-project-name :project) %)]
  (->> (:data api-response)
   no-empty-projects
   group-by-jira-project)))
