(ns toggl.data)

(def token (environ.core/env :toggl-token))

; The name of your application or your email address so we can get in touch in
; case you're doing something wrong.
(def user-agent (environ.core/env :toggl-user-agent))
