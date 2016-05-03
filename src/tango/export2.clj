(ns tango.export2
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.string :as cstr]
            [clojure.pprint :as cpp]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [taoensso.timbre :as log]
            ))

;; Sample data
(defn class-results[]
  [{:class-name              "Disco Freestyle B-klass J Po"
    :event-number "3A"
    :result {:round        "S"
             :adjudicators [{:number 2}
                            {:number 4}
                            {:number 5}]
             :dances       [{:name "X-Quick Forward"}
                            {:name "Quick"}]
             :result-array [{:dancer-number 30
                             :marks         [true false true]}
                            {:dancer-number 31
                             :marks         [true true true]}
                            {:dancer-number 32
                             :marks         [false false true]}]}}
                    ])

(defn activities-with-result []
[{:activity/name "Hiphop Singel Guld J1",
  :activity/number "3A"
  :round/name "Semifinal",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 0}
    {:adjudicator/number 1}
    {:adjudicator/number 5}]},
  :round/dances [{:dance/name "Medium"}],
  :result/_activity
  [{:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 143}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 141}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 2},
    :result/participant {:participant/number 141}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 3},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 4},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 144}}]}
 {:activity/name "Hiphop Singel Brons J1",
  :activity/number "4A"
  :round/name "Direct Final",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 2}
    {:adjudicator/number 3}
    {:adjudicator/number 4}]},
  :round/dances [{:dance/name "Medium"}]}
 {:activity/name "Hiphop Singel Brons U",
  :activity/number "4A",
  :round/name "Direct Final",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 0}
    {:adjudicator/number 1}
    {:adjudicator/number 5}]},
  :round/dances [{:dance/name "Medium"}]}
 {:activity/name "Hiphop Singel Brons B2",
  :activity/number "4A"
  :round/name "Semifinal",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 2}
    {:adjudicator/number 3}
    {:adjudicator/number 4}]},
  :round/dances
  [{:dance/name "Medium"}
   {:dance/name "Medium"}
   {:dance/name "Medium"}]}
 {:activity/name "Hiphop Singel Silver B2",
  :activity/number "4A"
  :round/name "Semifinal",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 0}
    {:adjudicator/number 1}
    {:adjudicator/number 5}]},
  :round/dances
  [{:dance/name "Medium"}
   {:dance/name "Medium"}
   {:dance/name "Medium"}
   {:dance/name "Medium"}],
  :result/_activity
  [{:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 83}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 84}}

   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 85}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 82}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 80}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 86}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 0},
    :result/participant {:participant/number 89}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 83}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 85}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 84}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 82}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 87}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 89}}]}
 {:activity/name "Hiphop Singel Brons U",
  :activity/number "4A"
  :round/name "Presentation",
  :round/panel
  {:adjudicator-panel/adjudicators
   [{:adjudicator/number 0}
    {:adjudicator/number 1}
    {:adjudicator/number 5}]},
  :round/dances [{:dance/name "Medium"} {:dance/name "Medium"}]}])

(defn activity-result [] (first (activities-with-result) ) )


(defn make-file-name-with-timestamp
  ;; Adds the current time at the end
  ;; of the filename
  [file-name]
  (let [time-now (t/now)
        time-formatter (tf/formatters :hour-minute-second)
        time-now-formatted (cstr/replace (tf/unparse time-formatter time-now) \: \_)
        new-file-name (str file-name "." time-now-formatted)]
    new-file-name
    ))

(defn make-copy-with-timestamp
 ;; Copies the file and adds current time at the end
 ;; of the filename.
 [file-name]
 (let [file-contents (slurp file-name)
       new-file-name (make-file-name-with-timestamp file-name)]
   (spit new-file-name file-contents)))

(defn short-round-name [round-name]
  (condp = round-name
    "Semifinal" "S"
    round-name))

(:result/_activity activity-result)

(def result-facts 
  [{:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 143}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 2},
    :result/participant {:participant/number 141}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 3},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 144}}])

(defn get-dancers [result-facts]
  (vec (sort  (apply hash-set (map #(get-in % [:result/participant :participant/number]) result-facts)))))

(defn get-mark [result-facts dancer-number adjudicator-number]
  (let [marks (filterv #(= dancer-number (get-in % [:result/participant :participant/number]))
                       (filter #(= adjudicator-number (get-in % [:result/adjudicator :adjudicator/number])) result-facts))]
    (if (empty? marks)
      false
      (get (first marks) :result/mark-x false))))

(defn get-marks-for-dancer [result-facts adjudicator-numbers dancer-number]
  (mapv (partial get-mark result-facts dancer-number) adjudicator-numbers))

(defn result-facts->result-array [result-facts adjudicators]
  (let [adjudicator-numbers (map :adjudicator/number adjudicators)
        dancer-numbers (get-dancers result-facts)]
    (mapv #(hash-map :dancer-number %
                     :marks (get-marks-for-dancer result-facts adjudicator-numbers %))
          dancer-numbers)))

(defn activity-result->class-result [activity-result]
  {:class-name (:activity/name activity-result)
   :event-number (:activity/number activity-result)
   :result {:round (short-round-name (:round/name activity-result))
            :adjudicators (vec (map
                                #(hash-map :number (:adjudicator/number %))
                                (get-in activity-result [:round/panel :adjudicator-panel/adjudicators])))
            :dances (vec (map #(hash-map :name (:dance/name %))
                                (get-in activity-result [:round/dances])))
            :result-array (result-facts->result-array (:result/_activity activity-result)
                                                      (get-in activity-result
                                                              [:round/panel
                                                               :adjudicator-panel/adjudicators]))}})

(def adjudicators
  [{:adjudicator/number 1}
   {:adjudicator/number 2}
   {:adjudicator/number 3}
   {:adjudicator/number 4}])

(def result-facts
  [{:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 143}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 141}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 2},
    :result/participant {:participant/number 141}}
   {:result/mark-x false,
    :result/adjudicator {:adjudicator/number 3},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 4},
    :result/participant {:participant/number 141}}
   {:result/mark-x true,
    :result/adjudicator {:adjudicator/number 1},
    :result/participant {:participant/number 144}}])


;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn get-xml-from-file [xml-file-path]
  (xml/parse (java.io.FileInputStream. xml-file-path)))


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

(defn make-adj-list-node [adjudicators]
  (xml/element :AdjList {} (reduce #(conj %1 (xml/element :Adjudicator {:Seq (count %1) :Number (:number %2)})) [] adjudicators)))

(defn make-dance-list-node [dances]
  (xml/element :DanceList {} (reduce #(conj %1 (xml/element :Dance {:Seq (count %1) :Name (:name %2)})) [] dances)))

(defn make-result-node [result round seq]
  (let [result-array (:result-array result)
        dances (:dances result)
        dance-qty (count dances)
        adjudicators (:adjudicators result)
        adj-qty (count adjudicators)]
    (xml/element :Result {:Seq seq
                          :Round round
                          :AdjQty adj-qty
                          :D3 "0"}
                 
                 (make-adj-list-node (:adjudicators result))
                 (make-dance-list-node (:dances result))
                 (xml/element :ResultArray {} 
                              (reduce #(conj %1 (make-couple-node %2 (count %1) dance-qty adj-qty))
                                      []
                                      result-array))
                 ))
  )

(defn fix-event [form]
  (let [new-attrs (merge (:attrs form) {:Status 1})]
    (merge form {:attrs new-attrs})))

(defn fix-class [class class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Results) (merge form {:attrs   {:Qty (inc (count (:content form)))}
                                              :content (conj (vec (:content form))
                                                             (make-result-node
                                                              (get-in class-result [:result] )
                                                              (get-in class-result [:result :round] )
                                                              (count (:content form))))})
        :else form))
    class))

(defn add-results-to-dp-xml [xml-data class-result]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      (:class-name class-result))
                                 (fix-class form class-result)
                                 form)
        (= (:tag form) :Event) (if (= (:EventNumber (:attrs form))
                                      (:event-number class-result))
                                (fix-event form)
                                form)
        :else form))
    xml-data))

(defn smoke-test2 []
  (let [in-xml (get-xml-from-file "test/tango/examples/real-example-kungsor.xml")
        cl-res (activity-result->class-result activity-result)
        out-xml (add-results-to-dp-xml in-xml cl-res)]
    (spit "export6.xml" (xml/emit-str out-xml))))
(defn smoke-test []
  (let [in-xml (get-xml-from-file "test/tango/examples/real-example.xml")
        out-xml (add-results-to-dp-xml in-xml (first class-results))]
    (spit "export5.xml" (xml/emit-str out-xml))))

;; TODO - should be the callers responsibillity to provide correct format
(defn- transform-result [activities-with-result]
  
  ;; TODO - perform transform and export
  (if :export-is-awsome
    true
    false))

;; http://nakkaya.com/2010/03/27/pretty-printing-xml-with-clojure/
(defn ppxml [xml]
  (let [in (javax.xml.transform.stream.StreamSource.
            (java.io.StringReader. xml))
        writer (java.io.StringWriter.)
        out (javax.xml.transform.stream.StreamResult. writer)
        transformer (.newTransformer 
                     (javax.xml.transform.TransformerFactory/newInstance))]
    (.setOutputProperty transformer 
                        javax.xml.transform.OutputKeys/INDENT "yes")
    (.setOutputProperty transformer 
                        "{http://xml.apache.org/xslt}indent-amount" "2")
    (.setOutputProperty transformer 
                        javax.xml.transform.OutputKeys/METHOD "xml")
    (.transform transformer in out)
    (-> out .getWriter .toString)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
;; TODO - need all participants
(defn export-results [activities-with-result export-path]
  (log/info (str "Export Results to " export-path " with " activities-with-result))
  (let [in-xml (get-xml-from-file export-path)
        class-results (map activity-result->class-result activities-with-result)
        out-xml (reduce add-results-to-dp-xml in-xml class-results)
        out-xml-orig (reduce add-results-to-dp-xml in-xml [])]
    (make-copy-with-timestamp export-path)
    (spit (str "orig_" export-path) (ppxml (xml/emit-str out-xml-orig)))
    (spit export-path (ppxml (xml/emit-str out-xml)))))

(defn smoke-test [] (export-results (activities-with-result) "dp.xml"))

;; (smoke-test)




;; :activity/number -> 3A -> <Event EventNumber="3A" ... Status="1" />
;; 











