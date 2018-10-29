(ns cfn
  (:require [clj-yaml.core :as yaml]
            [clojure.walk :as walk])
  (:import [org.yaml.snakeyaml Yaml TypeDescription DumperOptions DumperOptions$FlowStyle]
           [org.yaml.snakeyaml.constructor Constructor]
           [org.yaml.snakeyaml.representer Representer BaseRepresenter]
           [org.yaml.snakeyaml.nodes Tag]
           [org.yaml.snakeyaml.nodes ScalarNode]))

(defmacro deftag [name]
  `(defrecord ~name ~['value]
     yaml/YAMLCodec
     ~(list 'encode ['this]
        'this)
     (~'decode [~'this ~'keywords]
      (new ~name ~'value))))

(defmacro make-type-description [tag]
  `(proxy ~['TypeDescription] ~[tag (str "!" tag)]
     (~'newInstance ~['node]
      (new ~tag ~'(.getValue node)))))

(deftag Sub)
(deftag Ref)

(defn make-yaml []
  (let [representers-field (-> Representer
                               .getSuperclass
                               .getSuperclass
                               (.getDeclaredField "representers"))
        representer (Representer.)
        yaml (Yaml. (Constructor.) representer (doto (DumperOptions.)
                                                 (.setDefaultFlowStyle DumperOptions$FlowStyle/BLOCK)))]
    (.setAccessible representers-field true)
    (.put (.get representers-field representer)
          Sub (reify org.yaml.snakeyaml.representer.Represent
                (representData [this data]
                  (ScalarNode. (Tag. "!Sub") (.-value data) nil nil nil))))
    (.put (.get representers-field representer)
          Ref (reify org.yaml.snakeyaml.representer.Represent
                (representData [this data]
                  (ScalarNode. (Tag. "!Ref") (.-value data) nil nil nil))))
    (.addTypeDescription yaml (make-type-description Sub))
    (.addTypeDescription yaml (make-type-description Ref))
    yaml))

(defn find-refs [template]
  (let [refs (atom [])]
    (walk/postwalk #(when (or (= cfn.Sub (type %))
                              (= cfn.Ref (type %)))
                      (swap! refs conj %)
                      %)
                   template)
    @refs))

(defn validate-references [cfn]
  (let [referrables (set (concat (->> cfn :Parameters keys (map name))
                                 (->> cfn :Resources keys (map name))))
        references (set (->> (find-refs cfn)
                             (keep (fn [x]
                                     (condp = (type x)
                                       cfn.Ref [(:value x)]
                                       cfn.Sub (->> (re-seq #"\$\{([^\}]+)" (:value x))
                                                    (map second)
                                                    (filter #(not (.contains % "::")))))))
                             (apply concat)))
        unresolved-references (clojure.set/difference references referrables)]
    (when-not (empty? unresolved-references)
      (throw (ex-info (str "Unresolved references found: " unresolved-references)
                      {:unresolved unresolved-references})))))

(defn parse [cfn-yml]
  (let [cfn (yaml/decode (.load (make-yaml) cfn-yml) true)]
    (validate-references cfn)
    cfn))

(defn generate-string [cfn-data]
  (validate-references cfn-data)
  (.dump (make-yaml) (yaml/encode cfn-data)))

(defn load-and-print [filename]
  (println (.dump (make-yaml) (yaml/encode (parse (slurp filename))))))
