(ns pyjure.exceptions
  (:use [clojure.core.match :only [match]]
        [pyjure.utilities]
        [pyjure.runtime]
        [pyjure.exceptions]))

(defmacro define-exception [exname bases args fmt]
  (let [pyname (subs (name exname) 1)]
  `(do
     ;; TODO:
     ;; * pick a keyword
     ;; * pick a symbol
     ;; * defn the symbol as $error'ing with the keyword as tag
     ;; * register the keyword to the py mop, with a __dict__ that integrates it in the pyjure object system
     ~(comment `(defpyclass ~name ~supers
                  (defpyfield format ~fmt)
                  (defpyinit ~name ~args)))
     ;; (def-py-type ~exname ~(or bases $Exception))
     (def ~exname ~args ($error '~exname ~fmt ~@args)))))
