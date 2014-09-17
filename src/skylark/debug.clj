(ns skylark.debug
  (:use [skylark.utilities])
  (:require [clojure.repl]
            [clojure.tools.trace]))

;; debugging utilities while developing skylark

(reexport clojure.repl apropos pst)
(reexport-macro clojure.repl doc)
(reexport-macro clojure.tools.trace trace untrace)

(reexport-deferred skylark.core
  skylark to-reader position-stream lex parse desugar clarify cleanup analyze-continuations clojurify)