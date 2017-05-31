(ns toggl2jira.core
 (:require
  environ.core
  toggl.report
  cuerdas.core
  jira.core
  clj-time.format
  clj-time.core
  clojure.edn))

(def client-id (environ.core/env :toggl2jira-client-id))

(defn extract-ids
 "Attempt to extract a vector of IDs from a string"
 [s]
 (some->> s
  (re-find #"\[.+\]")
  clojure.edn/read-string
  (map str)))

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
 "Toggl reports durations in milliseconds but Jira wants mins."
 [ms]
 (str (int (/ ms (* 60 1000)))))

(defn jira-seconds->jira-duration
 "Jira provides a duration in seconds that can be converted to mins."
 [s]
 (str (int (/ s (* 60)))))

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
                                                     ; Strangely Jira needs the suffix "m" to recognise minutes when adding a new worklog (Jira assumes hours).
                                                     ; This isn't required when editing existing worklogs (Jira assumes minutes).
                                                     (str (toggl-times->jira-duration ts) "m")
                                                     (str desc " " (vec (map :id ts)))]))]
                                    (clojure.string/join "\n" (map log->out logs))))]
     (throw
      (Exception.
       (str "Toggl times missing from Jira!\n"
        (jira-log-recommendation simplified-times)))))))

  ; Ensure that every time in Jira exists in Toggl.
  (let [ids->bung-ids #(seq (remove toggl-indexed %))]
   (when-let [bung-times (seq (filter
                               #(ids->bung-ids (:toggl-ids %))
                               jira-times))]
    (let [next-bung-time (first bung-times)]
     (throw
      (Exception.
       (str
        "Bad Toggl IDs found in Jira.\n"
        "Bad IDs: " (ids->bung-ids (:toggl-ids next-bung-time)) "\n"
        "Jira URL: " (jira-time->url next-bung-time)))))))

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
  (let [times (fn [jira-time]
               (let [jira-time->toggl-times #(select-keys toggl-indexed (:toggl-ids %))
                     jira-duration (jira-seconds->jira-duration (:timeSpentSeconds jira-time))
                     toggl-times (vals (jira-time->toggl-times jira-time))
                     toggl-duration (toggl-times->jira-duration toggl-times)]
                (vector jira-duration toggl-duration)))
        times-equal? #(apply = %)
        bad-times (seq (remove (comp times-equal? times) jira-times))]
   (when bad-times
    (let [bt-times (map #(vector % (times %))
                    bad-times)
          [t [jira-duration toggl-duration]] (first bt-times)]
     (throw
      (Exception.
       (str
        "Inconsistent times found.\n"
        "Time in Toggl: " toggl-duration " mins\n"
        "Time in Jira: " jira-duration " mins\n"
        (jira-time->url t) "\n"
        "Jira time: " (pr-str t)))))))))

(defn do-it!
 []
 (let [toggl-times (toggl-items)
       jira-issues (toggl-times->jira-issues toggl-times)
       jira-times (jira-times-by-issue jira-issues)]
  (reconcile-jira-times toggl-times jira-times)))
