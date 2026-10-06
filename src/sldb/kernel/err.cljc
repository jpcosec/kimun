(ns sldb.kernel.err
  "Kernel error contract (docs/v2/03 §2): every kernel error is an `ex-info`
   whose data carries a namespaced `:type`. This is the single kernel namespace
   allowed to contain a reader conditional, because the class literal of a
   `catch` clause differs between hosts; every other kernel namespace uses
   `rescue` instead of `try`/`catch`.")

(defn raise
  "Throws an ex-info of `type` with message `msg` and extra `data`."
  [type msg data]
  (throw (ex-info msg (assoc data :type type))))

(defmacro rescue
  "Evaluates `body`; if an ex-info escapes, returns `(handler e)`. Errors that
   are not ex-info propagate unchanged."
  [handler & body]
  `(try ~@body
        (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e#
          (if (ex-data e#) (~handler e#) (throw e#)))))

(defmacro rescue-any
  "Evaluates `body`; if ANYTHING is thrown (ex-info or not, e.g. the reader's
   error on bytes that are not EDN), returns `(handler e)`. Only for code that
   inspects possibly damaged content and must report instead of raising."
  [handler & body]
  `(try ~@body
        (catch #?(:clj Exception :cljs :default) e#
          (~handler e#))))
