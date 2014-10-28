(ns tango.core
  (:gen-class)
  (:require [compojure.core :refer :all] ))


(defn gen-data [] 
  {
   :competitions
   [
    {
     :id "<guid1>"
     :name "SM"
     :location "Stockholm"
     :dancers 
     [
      {
       :id "<guid2>"
       :name "kalle"
       :email "kalle@foo.com"
      }
      {
       :id "<guid3>"
       :name "kajsa"
       :email "kajsa@foo.com"
      }
     ]
    }
   ]
  }
 )

(gen-data)
