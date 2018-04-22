(ns time.core
 (:require
  clj-time.core
  clj-time.format
  clj-time.coerce
  [clojure.test :refer [deftest is]]))

(defn truncate-time-to-format
 [time format]
 (if (= org.joda.time.format.DateTimeFormatter (type format))
  (clj-time.format/parse format (clj-time.format/unparse format time))
  (truncate-time-to-format time (clj-time.format/formatters format))))

(defn ->yyyy-mm
 [d]
 (clj-time.format/unparse
  (clj-time.format/formatters :year-month)
  d))

(defn ->format
 [fmt d]
 (clj-time.format/unparse
  (clj-time.format/formatters fmt)
  d))

(defn format->
 [fmt s]
 (clj-time.format/parse
  (clj-time.format/formatters fmt)
  s))

(defn ->iso8601
 [d]
 (clj-time.format/unparse
  (clj-time.format/formatters :date-time)
  d))

(defn iso8601->
 [s]
 (clj-time.format/parse
  (clj-time.format/formatters :date-time)
  s))

(defn yyyy-mm->date
 [yyyy-mm]
 (clj-time.format/parse
  (clj-time.format/formatters :year-month)
  yyyy-mm))

(defn date-repeat
 "Similar to core range, but for dates. The args are different though, because an infinite list starting at time X with step Y is the primary use case."
 ([start] (date-repeat start (clj-time.core/days 1)))
 ([start step]
  (cond
   (string? start)
   (date-repeat (iso8601-> start) step)

   :else
   (let [next-step (fn [current-step]
                    (clj-time.core/plus current-step step))]
    (iterate next-step start))))
 ([start end step]
  (cond
   (string? start)
   (date-repeat (iso8601-> start) end step)

   (string? end)
   (date-repeat start (iso8601-> end) step)

   :else
   (take-while
    #(clj-time.core/before? % end)
    (date-repeat start step)))))

; TESTS

(deftest ??date-repeat
 (let [start (clj-time.core/date-time 2000 1 2)]
  (is (= [start
          (clj-time.core/plus start (clj-time.core/days 1))
          (clj-time.core/plus start (clj-time.core/days 2))
          (clj-time.core/plus start (clj-time.core/days 3))
          (clj-time.core/plus start (clj-time.core/days 4))]
         (take 5 (date-repeat start))))

  (is (= [start
          (clj-time.core/plus start (clj-time.core/days 2))
          (clj-time.core/plus start (clj-time.core/days 4))
          (clj-time.core/plus start (clj-time.core/days 6))
          (clj-time.core/plus start (clj-time.core/days 8))]
         (take 5 (date-repeat start (clj-time.core/days 2)))))

  (is (= [start
          (clj-time.core/plus start (clj-time.core/days 2))
          (clj-time.core/plus start (clj-time.core/days 4))
          (clj-time.core/plus start (clj-time.core/days 6))]
         (date-repeat
          start
          (clj-time.core/date-time 2000 1 9)
          (clj-time.core/days 2))))))
