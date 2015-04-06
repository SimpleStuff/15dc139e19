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

(deftest repetitions
  (testing "Eliminate repetitions"
    (with-open [out-file (java.io.FileWriter. "./test/repetitions.txt")]
      (binding [*out* out-file]
        (dorun (map #(hunter/hunt %1) all-src-ns))))
    (let [repetitions-found (slurp "./test/repetitions.txt")]
      ;(is (= "" (with-out-str (hunter/hunt '(tango.core)))))
      (is (= true (empty? repetitions-found)) "See ./test/repetitions.txt for details"))))


;; fixa simple check
;; fixa eastwood
