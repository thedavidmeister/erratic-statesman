; https://github.com/toggl/toggl_api_docs/blob/master/reports.md
(ns toggl.report
 (:require
  environ.core))

(def base-url "https://toggl.com/reports/api/v2")

(def token (environ.core/env :toggl-token))
