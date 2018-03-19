(ns toggl.api
 (:require
  toggl.data))

(def base-url "https://toggl.com/api/v8/")

(defn with-auth
 [options]
 (merge
  {:basic-auth [toggl.data/token "api_token"]}
  options))

(defn with-agent
 [options]
 (assoc-in
  options
  [:query-params :user_agent]
  toggl.data/user-agent))

(defn with-defaults
 [options]
 (-> options
  with-auth
  with-agent))

(defn with-page [options page] (assoc-in options [:query-params :page] page))

(defn throw-bad-response!
 [response]
 (when-not (= 200 (:status response))
  (throw (Exception. (:body response)))))

(defn parse-body
 [response]
 (-> response :body cheshire.core/parse-string clojure.walk/keywordize-keys))

(defn api!
 ([endpoint] (api! endpoint {}))
 ([endpoint options]
  (let [url (str base-url endpoint)
        options (with-defaults options)
        request (org.httpkit.client/get
                 url
                 options)]

   (throw-bad-response! @request)
   (parse-body @request))))

(def workspaces (partial api! "workspaces"))
