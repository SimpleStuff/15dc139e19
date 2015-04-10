(ns tango.repetition-hunt
  (:require [clojure.test :refer :all]
            [repetition.hunter :as hunter]
            [clojure.tools.namespace.find :as nsp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hunt for repetitions

; (clojure.test/run-tests 'tango.repetition-hunt)

;; Find all namespaces in src
(def all-src-ns (clojure.tools.namespace.find/find-namespaces-in-dir (clojure.java.io/file "./src")))

(:require all-src-ns)

;; Async stuff that must be inside a go-block seems to be a false positive,
;; make a nicer filtering if needed.
(declare async-simple-filter)

(deftest repetitions
  (testing "Eliminate repetitions"
    (with-open [out-file (java.io.FileWriter. "./test/repetitions.txt")]
      (binding [*out* out-file]
        (dorun (map #(hunter/hunt %1) all-src-ns))))
    (let [repetitions-found (async-simple-filter (slurp "./test/repetitions.txt"))]
      (is (= true (empty? repetitions-found)) "See ./test/repetitions.txt for details"))))

(defn async-simple-filter [s]
  (clojure.string/replace
   s
   "2 repetitions of complexity 4\n\nLine 58 - tango.messaging:\n(async/>! system-ch (create-exception-message e))\n\nLine 71 - tango.messaging:\n(async/>! system-ch (create-exception-message e))\n\n======================================================================\n\n" ""))

;; fixa simple check

;;https://github.com/greglook/lein-hiera
;;https://github.com/jakemcc/lein-test-refresh
