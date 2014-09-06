(ns skylark.clojurifier
  (:use [clojure.core.match :only [match]]
        [skylark.debug :exclude [clojurify]]
        [skylark.utilities]
        [skylark.parsing]
        [skylark.runtime]))

;; TODO? have a previous pass transform everything in A-normal form,
;; where all arguments to functions are trivial (constant, function, variable)?
;; TODO: transform every branch point into binding-passing style?

(declare C create-binding)

(defrecord Environment [level local outer global])
;; level: 0 for global, incremented when you descend into scope
;; local: map of names to getters, or nil if level is 0
;; outer: the next outer non-global environment, or nil if only global is next
;; global: the global environment

(declare &C &C* &Cargs)

(def reserved-ids ;; otherwise valid identifiers that need be escaped
  #{"def" "if" "let" "do" "quote" "var" "fn" "loop" "recur" "throw" "try" "monitor-enter"
    "new"}) ;; also set! . &

(defn local-id [x] (symbol (if (reserved-ids x) (str "%" x) x)))
(defn &resolve-id [x] (fn [E] [(local-id x) E])) ;; TODO: distinguish global from local
(defn builtin-id [x] (symbol (str "$" (name x))))
(defn &resolve-constant [x]
  (fn [E] [(case (first x)
             (:integer :string :bytes) (second x)
             (builtin-id x)) E]))

(defn &C [x]
  (let [mx (meta x)]
    (letfn [(m [a] (if (or (symbol? a) (list? a) (vector? a)) (with-meta a mx) a))
            (m* [& a] (m a))
            (M [n a] (with-meta a (merge mx n)))
            (&r [x] (&return (m x)))
            (&k [k r] (&let [r (&C r)] (m `(~@k ~r))))]
      (match [x]
        [nil] &nil
        [[:id s]] (&bind (&resolve-id s) &r)
        [[:suite [:bind [:id s] :as n a] & r]]
        (&let [a (&C a) * (&k `(~'let [~(local-id s) ~a]) (vec* :suite r))])
        [[:suite [:unbind [:id s] :as n] & r]]
        (&k `(~'let [~s nil]) (vec* :suite r))
        [[:suite]] (&C [:constant [:None]])
        [[:suite a]] (&C a)
        [[:suite a b & r]] (&let [a (&C a) * (&k `(do ~a) (vec* :suite b r))])
        [[:bind [:id s] :as n a]] (&C a) ;; no suite to consume the binding
        [[:unbind [:id s]]] &nil
        [[:constant c]] (&resolve-constant c)
        [[:builtin b & as]]
        (&let [as (&C* as)] (m `(~(builtin-id b) ~@as)))
        :else (do (comment
        [[:unwind-protect body protection]]
        (&m (&tag :unwind-protect (&C body) (&C protection)))
        [[:handler-bind body handler]] (&m (&tag :handler-bind (&C body) (&C handler)))
        [[:builtin f & a]] (&let [a (&C* a)] (w :builtin f a))
        [[(:or ':from ':import ':constant ':break ':continue) & _]] (&x)
        [[h :guard #{:suite :return :raise :while :if} & a]] (&m (&tag* h (&C* a)))
        [[h :guard #{:yield :yield-from} a]]
        (&do (&assoc-in [:generator?] true) (&m (&tag h (&C a))))
        [[:handler-bind [:id s] :as target body handler]]
        (&let [type (&C type)
               _ (&assoc-in [:vars s :bound?] true)
               body (&C body)]
              (v :except type target body))
        [[:call f a]] (&m (&tag :call (&C f) (&Cargs a)))
        [[:class [:id s] :as name args body]]
        (&m (&let [args (&Cargs args)
                   _ (&assoc-in [:vars s :bound?] true)]
                  (let [[body innerE] ((&C body) nil)]
                    (if (:generator? innerE)
                      ($syntax-error x "invalid yield in class %a" [:name] {:name s})
                      (M (check-effects x innerE false) :class name args body)))))
        [[:argument id type default]]
        ;; handles argument in the *outer* scope where the function is defined,
        ;; *NOT* in the inner scope that the function defines (see :function for that)
        (&m (&into [:argument id] (&C type) (&C default)))
        [[:function args return-type body]]
        (&m (&let [args (&Cargs args) ; handle type and default
                   return-type (&C return-type)]
                  (let [[_ innerE] ((&map #(&assoc-in [:vars %] {:bound? true :locality :param})
                                          (args-vars args)) nil)
                        [body innerE] ((&C body) innerE)]
                    (M (check-effects x innerE true)
                       :function args return-type body))))
        :else)
        ($syntax-error x "unexpected expression %s during clarification pass"))))))

(defn &C* [xs] (&map &C xs))
(def &Cargs (&args &C))

(defn clojurify [x]
  (let [[x E] ((&C x) nil)]
    x))

(comment
  "
def foo(a):
  x=1
  if a: x=2
  return x
"
  (defn foo [a] (let [x 1] (-> ($if a [2] [x]) (fn [[x]] x))))

  "
def foo(a):
  x=1;
  if a:
    return x
  elif bar(a):
    y = 3
  else:
    y = 4
    z = 5
  if y == 4:
    return z
"
  (defn foo [a]
    (let [x 1]
      ((fn [k] ($cond a x
                      ($call bar a) (k 3 nil false)
                      :else (k 4 5 true)))
       ;; no need to pass a witness for y: it is always bound in all branches that reach k
       ;; on the other hand, our analysis doesn't prove that z is unbound, so we check
       (fn [y z z?] ((fn [k] ($cond (= y 4) ($check-bound z? z) ;
                                    :else (k)))
                (fn [] (return $None))))))))
;; Further passes may show that some functions are used only once, and thus may be inlined,

