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

(defn get-zip []
  (let [test-file
        (slurp (str "test/tango/examples/real-example-kungsor.xml"))]
    (zip/xml-zip
      (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes test-file))))))

(defn get-xml []
  (let [test-file
        (slurp (str "test/tango/examples/real-example-kungsor.xml"))]
    (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes test-file)))))

;(def zipper (get-zip))
;
;(zx/attr (first (zx/xml-> zipper :CompData)) :Place)
;
;(zx/xml-> zipper :Class)
;
;(zx/attr (first (zx/xml-> zipper :ClassList :Class)) :Name)
;
;(zx/xml-> zipper :ClassList :Class)
;
;(first (zx/xml-> zipper :ClassList :Class))
;(xml/emit-str (xml/element :Result {:Seq 0}))
;; Producera ett <Result> i <Results>

;(def zipper (zip/xml-zip (get-xml)))
;
;(defn tree-edit
;  "Take a zipper, a function that matches a pattern in the tree,
;   and a function that edits the current location in the tree.  Examine the tree
;   nodes in depth-first order, determine whether the matcher matches, and if so
;   apply the editor."
;  [zipper matcher editor]
;  (loop [loc zipper]
;    (if (zip/end? loc)
;      (zip/root loc)
;      (if-let [matcher-result (matcher loc)]
;        (let [new-loc (zip/edit loc editor)]
;          (if (not (= (zip/node new-loc) (zip/node loc)))
;            (recur (zip/next new-loc))))
;        (recur (zip/next loc))))))
;
;;; match predicate, all book tags
;(defn match-book? [loc]
;  (let [tag (:tag (zip/node loc))]
;    ;; true if tag is of type <path>
;    (= :Class tag)))
;
;(def test-names
;  {"Hiphop Singel Guld U" "XXXXXXXXXXXXXXXXX"})
;
;;; edit function
;(defn editor [node]
;  (let [id (-> node :attrs :Name)
;        new-content (conj
;                      (:content node)
;                      (xml/element :price {} "XX"               ;(get test-names id id)
;                                   ))]
;    (assoc-in node [:content] new-content)))
;
;(def edited (tree-edit zipper match-book? editor))
;
;edited
;(xml/indent-str
;  edited)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def raw-xml (get-xml))

;; TODO - kolla om results finns pa icke startade
(let [content (get-in raw-xml [:content])
      class-list (nth content 2)
      class-to-fix (first (filter #(= "Hiphop Singel Brons B1" (:Name (:attrs %))) (:content class-list)))
      class-results (first (filter #(= :Results (:tag %)) (:content class-to-fix)))
      new-results (conj (:content class-results) {:tag     :Result
                                                  :attrs   {:hej "Marten"}
                                                  :content nil})]

  ;new-results
  (assoc class-results :content new-results)
  )

(def new-stuff
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      "Hiphop Singel Brons B1")
                                 {:tag     :Class
                                  :attrs   {:Name "Changed Class"}
                                  :content nil}
                                 form)
        :else form))
    raw-xml))

(spit "export3.xml" (with-out-str (clojure.xml/emit new-stuff)))


(def raw-xml-2
  (xml/parse (java.io.FileInputStream. "test/tango/examples/real-example.xml")))

(def class-results [{:class-name "Disco Freestyle B-klass J Po"
                     :result {:adjudicators [{:number 2}
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

(def a-result-array (get-in (first class-results) [:result :result-array] ))
(def a-couple-data (first a-result-array))
(def a-marks (:marks a-couple-data))

(defn get-dance-count-from-class-node [class-node]
  (let [dance-list-node (first (filter #(= (:tag %) :DanceList) (:content class-node)))]
    (count (:content dance-list-node))))

(defn make-mark-node [mark seq]
  (xml/element :Mark
               {:Seq seq
                :X (if mark "X" " ")
                :D3 ""
                :A ""
                :B "Awesome4"}))

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

(defn make-result-node [result-array seq dance-qty adj-qty]
  (xml/element :Result
               {:Seq seq
                :Round "Awsome3"
                :AdjQty adj-qty
                :D3 "0"}
               (reduce #(conj %1 (make-couple-node %2 (count %1) dance-qty adj-qty)) [] result-array)))

(defn fix-class [class class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Results) (merge form {:attrs   {:Qty (inc (count (:content form)))}
                                              ;; TODO - ML fixar och noterar saknad data
                                              :content (conj (vec (:content form))
                                                             ;(xml/element :Foo {})
                                                             (make-result-node
                                                              (get-in class-result [:result :result-array] )
                                                              (count (:content form))
                                                              (get-dance-count-from-class-node class)
                                                              (count  (get-in class-result [:result :adjudicators] )))
                                                             )
                                              })
        :else form))
    class))

(defn new-stuff-2 [class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      "Disco Freestyle B-klass J Po")
                                 (fix-class form class-result)
                                 ;(xml/element :Class {:Name "Changed Class"})
                                 form)
        :else form))
    raw-xml-2))

(def class-node
  (xml/element :Class {}
               (xml/element :Foo {})
               (xml/element :DanceList {}
                            (xml/element :Dance {})
                            (xml/element :Dance {})
                            (xml/element :Dance {}))
               (xml/element :Bar {})))

(get-dance-count-from-class-node class-node)



(xml/parse (java.io.FileInputStream. "test/tango/examples/real-example-kungsor.xml"))

(def foo (xml/emit-str (new-stuff-2 (first class-results))))
(spit "export4.xml" foo )

;(make-mark-list-node a-marks)
;(make-couple-node a-couple-data 7 3 6)
;(make-result-node a-result-array 1 3)

;(defn sanitize [cmp]
;  (let [index (participant-index cmp)]
;    (clojure.walk/postwalk
;      (fn [form]
;        (cond
;          ;; replace participant-number with participant id in results
;          (:result/participant-number form) (dissoc
;                                              (assoc form :result/participant
;                                                          {:participant/id
;                                                           (get index
;                                                                (:result/participant-number form))})
;                                              :result/participant-number)
;
;          ;; adjudicator id should be in map form for lookup ref.
;          (:judging/adjudicator form) (assoc form :judging/adjudicator
;                                                  {:adjudicator/id (:judging/adjudicator form)})
;
;          ;; remove nil values
;          (map? form) (let [m (into {} (remove (comp nil? second) form))]
;                        (when (seq m)
;                          m))
;
;          :else form))
;      cmp)))

(def class-results [{:class-name "Disco Freestyle B-klass J Po"
                     :result {:ajudicators [{:number 2}
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
                    {:class-name "Disco Freestyle B-klass J Fl"
                     :result {:ajudicators [{:number 1}
                                            {:number 2}
                                            {:number 4}]
                              :dances [{:name "X-Quick"}]
                              :result-array [{:dancer-number 40
                                              :marks [true false false]}
                                             {:dancer-number 41
                                              :marks [true true true]}
                                             ]}}
                    ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
(defn export-results [stuff]
  (log/info (str "Export Results with " stuff)))

