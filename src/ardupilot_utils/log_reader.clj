(ns ardupilot-utils.log-reader
    (:require [ardupilot-utils.impl.log-reader :refer [find-next-message FORMAT-MESSAGE-ID
                                                       merge-format-message read-field]])
    (:import [org.apache.commons.io.input SwappedDataInputStream]
             [java.io EOFException]))

(defn parse-bin
  "Parse a bin file returning a lazy sequeonce of message maps.
   Any log corruption, or unexpected ending will be ingored, and the parser
   will attempt to recover parsing the stream"
  ([input]
   (let [reader (new SwappedDataInputStream input)]
     (parse-bin reader {128 {:name "FMT"
                             :fields [{:name :Type    :type \B}
                                      {:name :Length  :type \B}
                                      {:name :Name    :type \n}
                                      {:name :Format  :type \N}
                                      {:name :Columns :type \Z}]
                             :message-type :FMT}})))
  ([^SwappedDataInputStream reader formats]
   (lazy-seq
     (if-let [message-id (find-next-message reader)]
       (let [{:keys [fields message-type]} (get formats message-id)]
         (when-not (and fields message-type)
           (throw (ex-info "Unknown message format" {:message-id message-id})))
         (if-let [message (try
                            ; attempt to read all the fields out of the message
                            (persistent! (reduce (fn [read-fields {:keys [name type]}]
                                                     (assoc! read-fields name (read-field type reader)))
                                                 (transient {:message-type message-type}) fields))
                            (catch EOFException e))]
           (cons message (parse-bin reader (if (= message-id FORMAT-MESSAGE-ID)
                                             (merge-format-message formats message)
                                             formats)))
           (.close reader)))
       (.close reader)))))
