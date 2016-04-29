(ns tango.export2
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [taoensso.timbre :as log]
            ))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn get-xml-from-file [xml-file-path]
  (xml/parse (java.io.FileInputStream. xml-file-path)))

(def class-results [{:class-name "Disco Freestyle B-klass J Po"
                     :result {:round "S"
                              :adjudicators [{:number 2}
                                            {:number 4}
                                            {:number 5}]
                              :dances [{:name "X-Quick Forward"}
                                       {:name "Quick"}]
                              :result-array [{:dancer-number 30
                                              :marks [true false true]}
                                             {:dancer-number 31
                                              :marks [true true true]}
                                             {:dancer-number 32
                                              :marks [false false true]}]}}
                    ])

(defn get-dance-count-from-class-node [class-node]
  (let [dance-list-node (first (filter #(= (:tag %) :DanceList) (:content class-node)))]
    (count (:content dance-list-node))))

(defn make-mark-node [mark seq]
  (xml/element :Mark
               {:Seq seq
                :X (if mark "X" " ")
                :D3 ""
                :A ""
                :B ""}))

(defn make-mark-list-node [marks]
  (xml/element :MarkList
               {}
               (reduce #(conj %1 (make-mark-node %2 (count %1))) [] marks)))

(defn make-couple-node [couple-data seq dance-qty adj-qty]
  (xml/element :Couple
               {:Seq seq
                :DanceQty dance-qty
                :AdjQty adj-qty
                :Number (:dancer-number couple-data)
                :Recalled " "}
               (make-mark-list-node (:marks couple-data))
               ))

(defn make-result-node [result-array round seq dance-qty adj-qty]
  (xml/element :Result
               {:Seq seq
                :Round round
                :AdjQty adj-qty
                :D3 "0"}
               (reduce #(conj %1 (make-couple-node %2 (count %1) dance-qty adj-qty)) [] result-array)))

(defn fix-class [class class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Results) (merge form {:attrs   {:Qty (inc (count (:content form)))}
                                              :content (conj (vec (:content form))
                                                             (make-result-node
                                                              (get-in class-result [:result :result-array] )
                                                              (get-in class-result [:result :round] )
                                                              (count (:content form))
                                                              (get-dance-count-from-class-node class)
                                                              (count  (get-in class-result [:result :adjudicators] )))
                                                             )
                                              })
        :else form))
    class))

(defn add-results-to-dp-xml [xml-data class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      (:class-name class-result))
                                 (fix-class form class-result)
                                 ;(xml/element :Class {:Name "Changed Class"})
                                 form)
        :else form))
    xml-data))

(defn smoke-test []
  (let [in-xml (get-xml-from-file "test/tango/examples/real-example.xml")
        out-xml (add-results-to-dp-xml in-xml (first class-results))]
    (spit "export5.xml" (xml/emit-str out-xml))))

(smoke-test)

;; TODO - should be the callers responsibillity to provide correct format
(defn- transform-result [activities-with-result]
  ;; TODO - perform transform and export
  (if :export-is-awsome
    true
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API

;; TODO - provide paths for the correct files
(defn export-results [activities-with-result export-path original-path]
  (log/info (str "Export Results with " activities-with-result))
  (transform-result activities-with-result))

