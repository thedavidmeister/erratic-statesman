(ns toggl.api
 (:require
  toggl.data))

(def base-url "https://toggl.com/api/v8/")

(defn with-auth
 [options]
 (merge
  {:basic-auth [toggl.data/token "api_token"]}
  options))
