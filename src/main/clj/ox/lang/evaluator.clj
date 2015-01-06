(ns ox.lang.evaluator
  (:refer-clojure :exclude [eval apply macroexpand-1 macroexpand compile])
  (:require [clojure.java.io :as io]
            [ox.lang.environment :as env]
            [ox.lang.parser :as parser]))

(declare eval)

(defn read-eval-1
  "Implementation detail of read-eval. This form computes the read-eval of a
  form given that it has already been recursively read-eval'd.

  (let* ((env ...))
  (read-eval env '(read-eval FORM))
  => (eval env FORM)

  forms which do not start with read-eval are returned unmodified."
  [eval env tree]
  (if (= 'read-eval (first tree))
    (eval env (second tree))
    tree))

(defn read-eval
  "Recursively walks the given tree, read-evaluating children nodes first then
  read-eval-1ing the parent of the evaluated nodes. This pass exists so that
  datastructures like maps, floats and vectors can be implemented as part of
  bootstrap rather than being baked into a platform's implementation.

  When reading structures, users are required to read-eval their forms before
  macroexpanding or evaluating them."
  [eval env tree]
  (if (list? tree)
    (->> tree
         (map (partial read-eval eval env))
         (read-eval-1 eval env))
    tree))



(defn macroexpand-1
  [eval env tree]
  (if (and (list? tree)
           (:macro (env/get-meta (first tree))))
    (eval env tree)
    tree))

(defn fix [f x]
  (loop [x x]
    (let [x' (f x)]
      (if-not (= x x')
        (recur x')
        x))))

(defn macroexpand
  "Walks a previously read-eval'd tree, expanding macros occuring in the tree
  via macroexpand-1. Children are expanded first until they reach a fixed point,
  then the parent is expanded until it fixes as well."
  [eval env tree]
  (if (list? tree)
    (->> tree
         (map (partial macroexpand eval env))
         (fix (partial macroexpand-1 eval env)))
    tree))



(defn compute-fn-bindings [args vals]
  (loop [acc {}
         args args
         vals vals]
    (let [[arg & args'] args
          [val & vals'] vals]
      (if-not (empty? args)
        (if (= arg '&)
          (assoc acc (first args') vals)
          (recur (assoc acc arg val) args' vals'))
        acc))))

(declare interpreting-eval)

(defn interpreting-eval-1 [env form]
  (let [-e (comp second (partial interpreting-eval env))]
    (cond (or (list? form)
              (instance? clojure.lang.Cons form))
          ,,(let [[f & args] form]
              (case f
                (apply)
                ,,(let [[lambda & args]   args
                        _                 (assert (= 'fn* (first lambda)) "Not applying a fn*!")
                        bodies            (->> lambda rest
                                               (sort-by #(count (first %1))))
                        app-args          (concat (map -e (butlast args))
                                                  (-e (last args)))
                        body              (or (loop [bodies bodies]
                                                (let [[[fn-args & forms :as b] & bodies] bodies]
                                                  (if (= (count app-args)
                                                         (count fn-args))
                                                    b
                                                    (when-not (empty? bodies)
                                                      (recur bodies)))))
                                              (last bodies))
                        [fn-args & forms] body]
                    #(interpreting-eval
                      (->> (compute-fn-bindings fn-args app-args)
                           (env/push-locals env))
                      `(~'do* ~@forms)))

                (def*)
                ,,(let [[s m v & more] args]
                    (assert (symbol? s) "Not a binding symbol!")
                    (assert (or (nil? m) (map? m)) "Metadata not a mapv!")
                    (assert (empty? more) "More args to def than expected!")
                    [(-> env
                         (env/inter s v)
                         (env/set-meta s m))
                     s])

                (do*)
                ,,(do (doseq [f (butlast args)]
                        (-e f))
                      #(interpreting-eval env (last args)))

                (fn*)
                ,,[env form]

                (if*)
                ,,[env
                   (let [[pred left right & more] args]
                     (-e (if (-e pred) left right)))]

                (let*)
                ,,(let [[bindings & forms] args
                        bindings           (->> (for [[k v] bindings]
                                                  [k (-e v)])
                                                (into {}))
                        [_ res]            (interpreting-eval
                                            (env/push-locals env bindings)
                                            `(~'do* ~@forms))]
                    [env res])

                (letrc*)
                ,,(assert false "letrc isn't supported yet™")

                (quote)
                ,,[env (second form)]

                (invoke)
                ,,(let [[f & args] args]
                    [env (clojure.core/apply f (map -e args))])

                #(interpreting-eval env `(~'apply ~@(map -e form) '()))))

          (symbol? form)
          ,,(if (env/special? env form)
              [env form] ;; self-evaluate
              [env (->> form
                        (env/resolve env)
                        (env/get-value env))])

          :else ;; everything else is self-evaluating
          ,,[env form])))

(defn interpreting-eval
  "Walks a previously read-eval'd and macroexpand'd form, evaluating it by
  interpretation. Returns a pair [env, result]."
  [env form]
  (trampoline interpreting-eval-1 env form))

(defn interpreting-load
  [path]
  (->> (str path ".oxwclj")
       io/resource
       slurp
       parser/parse-file
       (reduce (fn [env form]
                 (->> form
                      (read-eval env)
                      (macroexpand env)
                      (interpreting-eval env)
                      first))
               (env/make-environment 'user))))
