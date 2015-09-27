(ns tango.import
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn- to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          (Double/parseDouble (clojure.string/replace prepared-string #"," "."))
          (re-seq #"^[-+]?\d+$" prepared-string)
          (Integer/parseInt (clojure.string/replace prepared-string #"\+" ""))
          :else s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn read-xml [file]
  (xml/parse (java.io.FileReader. file)))

(defn- str-count [seq]
  (str (count seq)))

(defn- get-name-attr [loc]
  (zx/attr loc :Name))

(defn- get-number-attr [loc]
  (to-number (zx/attr loc :Number)))

(defn- get-seq-attr [loc]
  (to-number (zx/attr loc :Seq)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Zipper implementation

(defn couple->map [couples-loc]
  (for [couple couples-loc]
    {:competitor/name (get-name-attr couple)
     :competitor/club (zx/attr couple :Club)
     :competitor/number (get-number-attr couple)
     :competitor/position (get-seq-attr couple)}))

(defn dance-list->map [dances-loc]
  (for [dance dances-loc]
    {:dance/name (get-name-attr dance)}))

(defn adjudicator-list->map [adjudicator-loc]
  (for [adjudicator adjudicator-loc]
    {:adjudicator/number (get-number-attr adjudicator)
     :adjudicator/position (get-seq-attr adjudicator)}))

(defn marks->map [marks-loc adjudicators]
  (for [mark marks-loc]
    {:result/adjudicator
     (first (filter
             #(= (:adjudicator/position %)
                 (get-seq-attr mark))
             adjudicators))
     :result/x-mark (= (zx/attr mark :X) "X")}))

(defn mark-list->map [result-couple-loc adjudicators]
  (for [couple result-couple-loc]
    {:competitor/number (get-number-attr couple)
     :competitor/recalled
     (condp = (zx/attr couple :Recalled)
       "X" :x
       "R" :r
       " " ""
       (str "unexpected recalled value"))
     :competitor/results (into [] (marks->map (zx/xml-> couple :MarkList :Mark) adjudicators))}))

(defn result-list->map [result-loc]
  (for [result result-loc]
    (let [adjudicators (adjudicator-list->map (zx/xml-> result :AdjList :Adjudicator))]
      {:result/round (zx/attr result :Round)
       :result/dance (first (dance-list->map (zx/xml-> result :DanceList :Dance)))
       :result/adjudicators (into [] adjudicators)
       :result/results (into [] (mark-list->map (zx/xml-> result :ResultArray :Couple) adjudicators))})))

(defn class-list->map [classes-loc]
  (for [class classes-loc]
    {:class/name (zx/attr class :Name)
     :class/position (get-seq-attr class)
     :class/adjudicator-panel (to-number (zx/attr class :AdjPanel))
     :class/competitors (into [] (couple->map (zx/xml-> class :StartList :Couple)))
     :class/dances (into [] (dance-list->map (zx/xml-> class :DanceList :Dance)))
     :class/results (into [] (result-list->map (zx/xml-> class :Results :Result)))}))

(defn competition->map [loc]
  (let [competition-data (first (zx/xml-> loc :CompData))]
    {:competition/name (get-name-attr competition-data)
     :competition/date (tcr/to-date
                        (tf/parse (tf/formatter "yyyy-MM-dd")
                                  (zx/attr competition-data :Date)))
     :competition/location (zx/attr competition-data :Place)
     :dance-perfect/flags {:adj-order-final (to-number (zx/attr competition-data :AdjOrderFinal)) }
     :competition/classes (into [] (class-list->map (zx/xml-> loc :ClassList :Class)))}))

(defn- create-import-info [version content status errors]
  {:file/version version
   :file/content content
   :file/import-status status
   :file/import-errors errors})

(defn dance-perfect->map [loc status errors]
  {:file/version (zx/attr loc :Version)
   :file/content (competition->map loc)
   :file/import-status status
   :file/import-errors errors})

(defn dance-perfect-xml->data [xml-src]
  (competition->map (zip/xml-zip xml-src)))

;; TODO : add generic exception handling
(defn import-file [path]
  {:pre [(string? path)]}
  (let [file (clojure.java.io/file path)]
    (if (.exists file)
      (let [xml-src (zip/xml-zip (clojure.xml/parse file))
            dance-perfect-data (dance-perfect->map xml-src :success [])]
        dance-perfect-data)
      (create-import-info "" [] :failed [:file-not-found]))))

;; TODO - add test
;; TODO - add exception catch
(defn import-file-stream [{:keys [content]}]
  (dance-perfect->map (zip/xml-zip (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes content))))
                      :success
                      []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export - this implementation is stale, look at is as experimental!

(defn- competitor->xml [competitor seq-nr]
  [:Couple {:Name (:competitor/name competitor)
            :Seq (str seq-nr)
            :License ""
            :Club (:competitor/club competitor)
            :Number (str (:competitor/number competitor))}])

(defn- competitors->dance-perfect-xml-start-list [competitiors]
  (into [:StartList {:Qty (str-count competitiors)}]
        (reduce (fn [start-list-xml competitor]
                    (conj start-list-xml (competitor->xml competitor (count start-list-xml))))
                  []
                  competitiors)))

(defn- classes->dance-perfect-xml-classes [classes]
  (reduce (fn [classes-xml class]
            (conj classes-xml [:Class
                               {:Name (:class/name class)
                                :Seq (str-count classes-xml)}
                               (competitors->dance-perfect-xml-start-list (:class/competitors class))
                               ]))
          []
          classes))

(defn- date->dance-perfect-format [date]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") date))

(defn data->dance-perfect-xml-data [version dance-perfect-data]
  (xml/sexp-as-element
   [:DancePerfect {:Version version}
    [:CompData {:Name (:competition/name dance-perfect-data)
                :Date (date->dance-perfect-format (:competition/date dance-perfect-data))
                :Place (:competition/location dance-perfect-data)}]
    [:AdjPanelList [:AdjList]]
    (into [:ClassList] (classes->dance-perfect-xml-classes (:competition/classes dance-perfect-data)))]))

(defn data->dance-perfect-xml [version dance-perfect-data]
  (xml/emit-str (data->dance-perfect-xml-data version dance-perfect-data)))

;; (defn data->dance-perfect-xml [version dance-perfect-data]
;;   (with-out-str (xml/emit (data->dance-perfect-xml-data version dance-perfect-data))))


;; (with-open [out-file (java.io.FileWriter. "foo.xml")]
;;   (xml/emit (data->dance-perfect-xml-data "4.1" (:file/content small-exampel-data)) out-file))

;; (xml/emit-str (xml/sexp-as-element [:foo {:foo-attr "M&M"}]))

;; (with-open [out-file (java.io.FileWriter. "foo.xml")]
;;   (xml/emit (xml/sexp-as-element [:foo {:foo-attr "M&M"}]) out-file))

;; (with-open [out-file (java.io.FileWriter. "foo.xml")]
;;   (xml/emit (xml/sexp-as-element (data->dance-perfect-xml-data "4.1" (:file/content small-exampel-data))) out-file))

;(spit "export.xml" (with-out-str (export-file 4.1 (:file/content small-exampel-data))))
;http://blog.fogus.me/2011/09/08/10-technical-papers-every-programmer-should-read-at-least-twice/

