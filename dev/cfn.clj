(ns cfn
  (:require [clj-yaml.core :as yaml])
  (:import [org.yaml.snakeyaml Yaml TypeDescription DumperOptions DumperOptions$FlowStyle]
           [org.yaml.snakeyaml.constructor Constructor]
           [org.yaml.snakeyaml.representer Representer BaseRepresenter]
           [org.yaml.snakeyaml.nodes Tag]
           [org.yaml.snakeyaml.nodes ScalarNode]))

(defmacro yaml-tag-record [tag]
  `(defrecord ~tag ~['value]
     yaml/YAMLCodec
     ~(list 'encode ['this]
        'this)
     (~'decode [~'this ~'keywords]
      (new ~tag ~'value))))

(defmacro make-type-description [tag]
  `(proxy ~['TypeDescription] ~[tag (str "!" tag)]
     (~'newInstance ~['node]
      (new ~tag ~'(.getValue node)))))

(yaml-tag-record Sub)
(yaml-tag-record Ref)

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

(defn parse [yml]
  (yaml/decode (.load (make-yaml) yml) true))

(defn generate-string [data]
  (.dump (make-yaml) (yaml/encode data)))

(defn load-and-print [filename]
  (println (.dump (make-yaml) (yaml/encode (parse (slurp filename))))))
