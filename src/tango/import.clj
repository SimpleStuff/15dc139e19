(ns tango.import
  (:require [clojure.xml :as xml]
            [clj-time.coerce :as tcr]
            [clj-time.format :as tf]))

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
;; Import
(defn read-xml [file]
  (xml/parse file))

(defn read-xml-string [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s))))

(defn- dance-perfect-xml-competitors->competitors [dp-competitors-xml]
  (mapv #(hash-map :competitor/name (get-in % [:attrs :Name])
                   :competitor/club (get-in % [:attrs :Club])
                   :competitor/number (to-number (get-in % [:attrs :Number])))
        dp-competitors-xml))

; Kan en klass bara ha en startlista? - make a test
(defn- dance-perfect-xml-classes->classes [dp-classes-xml]
  (mapv #(hash-map :class/name (get-in % [:attrs :Name])
                   :class/competitors
                   (dance-perfect-xml-competitors->competitors (:content (first (:content %)))))
        (:content dp-classes-xml))
  )

(defn- build-dance-perfect-xml-parts [dp-xml]
  (apply merge
         (for [node (xml-seq dp-xml)]
           (condp = (:tag node)
             :DancePerfect {:dance-perfect-xml node}
             :ClassList {:class-list-xml node}
             :CompData {:competition-data-xml node}
             nil))))

(defn- create-import-info [version content status errors]
  {:file/version version
   :file/content content
   :file/import-status status
   :file/import-errors errors})

(defn dance-perfect-xml->data [dp-xml]
  (let [xml-parts (build-dance-perfect-xml-parts dp-xml)
        competition-data (:competition-data-xml xml-parts)
        classes-xml (:class-list-xml xml-parts)]
    {:dance-perfect/version (get-in (:dance-perfect-xml xml-parts) [:attrs :Version])
     :competition/name (get-in competition-data [:attrs :Name])
     :competition/location (get-in competition-data [:attrs :Place])
     :competition/date (tcr/to-date
                        (tf/parse (tf/formatter "yyyy-MM-dd")
                                  (get-in competition-data [:attrs :Date])))
     :competition/classes (dance-perfect-xml-classes->classes classes-xml)
     }))

(defn import-file [path]
  {:pre [(string? path)]}
  (let [file (clojure.java.io/file path)]
    (if (.exists file)
      (let [xml-src (read-xml file)
            dance-perfect-data (dance-perfect-xml->data xml-src)]
        (create-import-info
         (:dance-perfect/version dance-perfect-data)
         (dissoc dance-perfect-data :dance-perfect/version)
         :success
         []))
      (create-import-info "" [] :failed [:file-not-found]))))

;{:tag :item, :attrs {:id "1"}, :content ["First"]}
;(spit "test.xml" (with-out-str (clojure.xml/emit simple-xml-map)))
(def small-exampel-data
  {:file/version "4.1",
   :file/content
   {:competition/name "TurboMegatävling",
    :competition/location "THUNDERDOME",
    :competition/date #inst "2014-11-22T00:00:00.000-00:00",
    :competition/classes
    [{:class/name "Hiphop Singel Star B", :class/competitors
      [{:competitor/name "Rulle Trulle", :competitor/number 1, :competitor/club "Rulles M&M"}
       {:competitor/name "Katchyk Wrong", :competitor/number 2, :competitor/club "Sccchhh"}]}
     {:class/name "Hiphop Singel Star J Fl", :class/competitors
      [{:competitor/name "Ringo Stingo", :competitor/number 20, :competitor/club "Kapangg"}
       {:competitor/name "Greve Turbo", :competitor/number 21, :competitor/club "OOoost"}]}]},
   :file/import-status :success, :file/import-errors []})

 ;; <Class Name="Hiphop Singel Star B" Seq="0">
 ;;      <StartList Qty="2">
 ;;        <Couple Name="Rulle Trulle" Seq="0" License="" Club="Rulles M&amp;M" Number="1"/>
 ;;        <Couple Name="Katchyk Wrong" Seq="1" License="" Club="Sccchhh" Number="2"/>
 ;;      </StartList>
 ;;    </Class>

(defn competitor->xml [competitor seq-nr]
  {:tag :Couple :attrs {:Name (:competitor/name competitor)
                        :Seq seq-nr
                        :License ""
                        :Club (:competitor/club competitor)
                        :Number (:competitor/number competitor)}})

(defn competitors->dance-perfect-xml-start-list [competitiors]
  [{:tag :StartList :attrs {:Qty (count competitiors)} :content
    (reduce (fn [start-list-xml competitor]
              (conj start-list-xml (competitor->xml competitor (count start-list-xml))))
            []
            competitiors)}])

(defn classes->dance-perfect-xml-classes [classes]
  (reduce (fn [classes-xml class]
            (conj classes-xml {:tag :Class :attrs
                               {:Name (:class/name class)
                                :Seq (count classes-xml)}
                               :content (competitors->dance-perfect-xml-start-list (:class/competitors class))}))
          []
          class-ex))

(defn date->dance-perfect-format [date]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") date))

(defn export-file [version dance-perfect-data]
  (xml/emit {:tag :DancePerfect :attrs {:Version version} :content
             [{:tag :CompData :attrs {:Name (:competition/name dance-perfect-data)
                                      :Date (date->dance-perfect-format (:competition/date dance-perfect-data))
                                      :Place (:competition/location dance-perfect-data)}}
              {:tag :AdjPanelList :content [{:tag :AdjList}]}
              {:tag :ClassList :content
               (classes->dance-perfect-xml-classes (:competition/classes dance-perfect-data))}]}))

(defn import-file-stream [{:keys [content]}]
  (dance-perfect-xml->data (read-xml-string content)))

;(spit "export.xml" (with-out-str (export-file 4.1 (:file/content small-exampel-data))))
;http://blog.fogus.me/2011/09/08/10-technical-papers-every-programmer-should-read-at-least-twice/
