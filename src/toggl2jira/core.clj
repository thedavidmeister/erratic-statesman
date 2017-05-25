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
       expand-ids #(map cuerdas.core/trim (clojure.string/split % #"\s"))]
  (some-> s
   regex-match
   drop-brackets
   expand-ids)))

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

(defn toggl-duration->jira-duration
 "Toggl reports durations in milliseconds but Jira wants decimal hours."
 [secs]
 (format "%.2f" (float (/ secs (* 60 60 1000)))))

(defn toggl-times->jira-duration
 [times]
 (toggl-duration->jira-duration (reduce + (map :dur times))))

(defn toggl-time->jira-url
 [t]
 (let [issue-key (first (extract-ids (:project t)))]
  (str "https://" jira.core/host "/browse/" issue-key)))

(defn toggl-time->simplified-toggl-time [time]
 (let [simplified (select-keys time [:description :start :project :id :dur :end])]
  (merge simplified
   {:dur_jira (toggl-duration->jira-duration (:dur simplified))})))

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
                                                                 :since "2017-01-01"
                                                                 :page 4}})]
  (no-empty-projects api-response)))

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

(defn toggl-time->date-str
 [toggl-time]
 (->> toggl-time
  :start
  (clj-time.format/parse-local (:date-time-no-ms clj-time.format/formatters))
  (clj-time.format/unparse-local (:date clj-time.format/formatters))))

(defn jira-time->toggl-candidates
 [jira-time]
 (let [date (jira-time->date-str jira-time)
       candidates (toggl.report/api!
                   "details"
                   {:query-params {:client_ids client-id
                                   :since date
                                   :until date}})]
  candidates))

(defn reconcile-jira-times
 [toggl-times jira-times]
 (let [
       toggl-indexed (into {}
                      (map
                       (fn [t] [(str (:id t)) t])
                       toggl-times))
       with-toggl-ids #(merge {:toggl-ids (extract-ids (:comment %))} %)
       jira-times (->> jira-times (map with-toggl-ids))
       reconcilable? (fn [jira-time]
                      (let [ids (extract-ids (:comment jira-time))]
                       (prn ids)))]
  ; Firstly, simply ensure that every work log in Jira has at least one Toggl
  ; ID associated with it.
  (when-let [dangling-times (seq (filter (comp nil? :toggl-ids) jira-times))]
   (let [next-dangling-time (first dangling-times)]
    (throw
     (Exception.
      (str "Jira time " (jira-time->url next-dangling-time) " missing toggl-id! Toggl candidates:\n"
       (pr-str
        (map
         toggl-time->simplified-toggl-time
         (jira-time->toggl-candidates next-dangling-time))))))))

  ; Ensure that every time in Toggl exists in the Jira logs.
  (let [jira-toggl-ids (into #{} (flatten (map :toggl-ids jira-times)))
        toggl-ids-not-in-jira (seq (remove jira-toggl-ids (keys toggl-indexed)))]
   (when toggl-ids-not-in-jira
    (let [simplified-times (map (comp toggl-time->simplified-toggl-time #(get toggl-indexed %))
                                toggl-ids-not-in-jira)
          with-date (fn [time]
                     (merge {:date (toggl-time->date-str time)}
                            time))
          jira-log-recommendation (fn [times]
                                   (let [with-dates (map with-date times)
                                         logs (group-by
                                               #(vector (:project %) (:date %) (:description %))
                                               with-dates)
                                         log->out (fn [[[project date desc] ts]]
                                                   (clojure.string/join
                                                    " | "
                                                    [
                                                     (toggl-time->jira-url (first ts))
                                                     date
                                                     (toggl-times->jira-duration ts)
                                                     (str desc " " (vec (map :id ts)))]))]
                                    (clojure.string/join "\n" (map log->out logs))))]
     (throw
      (Exception.
       (str "Toggl times missing from Jira!\n"
        (jira-log-recommendation simplified-times)))))))

  ; Ensure that every Toggl ID appears no more than once in the Jira logs.
  (let [toggl-ids (flatten (map :toggl-ids jira-times))
        freqs (frequencies toggl-ids)]
   (when-let [dupes (seq (remove (fn [[_ v]] (= 1 v))
                                 freqs))]
    (let [next-dupe-id (ffirst dupes)
          dupe-simplified (toggl-time->simplified-toggl-time (get toggl-indexed next-dupe-id))
          in-time? (fn [id time]
                    (let [ids (into #{} (:toggl-ids time))]
                     (boolean (ids id))))
          jira-dupes (filter #(in-time? next-dupe-id %) jira-times)]
     (throw
      (Exception.
       (str
        "Toggl time counted multiple times in Jira:\n"
        (prn-str dupe-simplified)
        "Appears in:\n"
        (prn-str (map jira-time->url jira-dupes))))))))

  ; Ensure that the Jira time logs equal the sum of their toggl counterparts.
  (let [jira-time->toggl-total-times (fn [jira-time]
                                      (let [ids (:toggl-ids jira-time)
                                            toggls (map
                                                    #(get toggl-indexed %)
                                                    ids)]
                                       ; (prn ids toggls (keys toggl-indexed) (toggl-indexed "543663016"))
                                       (reduce + (map :dur toggls))))]
   (jira-time->toggl-total-times (first jira-times)))))

(defn do-it!
 []
 (let [toggl-times (toggl-items)
       jira-issues (toggl-times->jira-issues toggl-times)
       jira-times (jira-times-by-issue jira-issues)]
  (reconcile-jira-times toggl-times jira-times)))
