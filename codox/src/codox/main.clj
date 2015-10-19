(ns codox.main
  "Main namespace for generating documentation"
  (:use [codox.utils :only (ns-filter add-source-paths)])
  (:require [codox.reader.clojure :as clj]
            [codox.reader.plaintext :as text]))

(defn- writer [{:keys [writer]}]
  (let [writer-sym (or writer 'codox.writer.html/write-docs)
        writer-ns (symbol (namespace writer-sym))]
    (try
      (require writer-ns)
      (catch Exception e
        (throw
         (Exception. (str "Could not load codox writer " writer-ns) e))))
    (if-let [writer (resolve writer-sym)]
      writer
      (throw
         (Exception. (str "Could not resolve codox writer " writer-sym))))))

(defn- macro? [var]
  (= (:type var) :macro))

(defn- read-macro-namespaces [& paths]
  (->> (apply clj/read-namespaces paths)
       (map (fn [ns] (update-in ns [:publics] #(filter macro? %))))
       (remove (comp empty? :publics))))

(defn- merge-namespaces [namespaces]
  (for [[name namespaces] (group-by :name namespaces)]
    (assoc (first namespaces) :publics (mapcat :publics namespaces))))

(defn- cljs-read-namespaces [& paths]
  ;; require is here to allow Clojure 1.3 and 1.4 when not using ClojureScript
  (require 'codox.reader.clojurescript)
  (let [reader (find-var 'codox.reader.clojurescript/read-namespaces)]
    (merge-namespaces
     (concat (apply reader paths)
             (apply read-macro-namespaces paths)))))

(def ^:private namespace-readers
  {:clojure       clj/read-namespaces
   :clojurescript cljs-read-namespaces})

(defn- add-var-defaults [vars defaults]
  (for [var vars]
    (-> (merge defaults var)
        (update-in [:members] add-var-defaults defaults))))

(defn- add-ns-defaults [namespaces defaults]
  (for [namespace namespaces]
    (-> (merge defaults namespace)
        (update-in [:publics] add-var-defaults defaults))))

(defn- read-namespaces
  [{:keys [language root-path source-paths include exclude metadata]}]
  (-> (namespace-readers language)
      (apply source-paths)
      (ns-filter include exclude)
      (add-source-paths root-path source-paths)
      (add-ns-defaults metadata)))

(defn- read-documents [{:keys [doc-paths]}]
  (if (seq doc-paths) (apply text/read-documents doc-paths)))

(def defaults
  {:language     :clojure
   :root-path    (System/getProperty "user.dir")
   :source-paths ["src"]
   :doc-paths    ["doc"]
   :output-path  "target/doc"
   :metadata     {}})

(defn generate-docs
  "Generate documentation from source files."
  ([]
     (generate-docs {}))
  ([options]
     (let [options    (merge defaults options)
           write-fn   (writer options)
           namespaces (read-namespaces options)
           documents  (read-documents options)]
       (write-fn (assoc options
                        :namespaces namespaces
                        :documents  documents)))))
