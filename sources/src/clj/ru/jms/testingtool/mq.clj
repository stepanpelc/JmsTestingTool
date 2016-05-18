(ns ru.jms.testingtool.mq
  (:import (javax.jms QueueConnectionFactory Connection Session Queue MessageConsumer Message TextMessage QueueBrowser MessageProducer)
           (java.util Enumeration)
           (java.io IOException))
  (:use ru.jms.testingtool.mq-init))

(defn ^Session get-session [connection-info]
  (try
    (let [^QueueConnectionFactory qcf (create-qcf connection-info)
          ^Connection q (.createConnection qcf)
          _ (.start q)]
      (.createSession q false Session/AUTO_ACKNOWLEDGE))
    (catch Exception e
      (throw (IOException. e)))))

(defn ^Queue get-queue [^Session s queue-info]
  (.createQueue s (:name queue-info)))

(defn safe-destination-to-str [destination]
  (if (nil? destination)
    nil
    (.getQueueName destination)))

(defn safe-to-long [^Long val]
  (if (nil? val) nil (Long. (str val))))

(defn safe-to-int [val]
  (if (nil? val) nil (Integer. (str val))))

(defn safe-to-short [val]
  (if (nil? val) nil (Short. (str val))))

(defn safe-to-double [val]
  (if (nil? val) nil (Double. (str val))))

(defn safe-to-float [val]
  (if (nil? val) nil (Float. (str val))))

(defn safe-to-boolean [val]
  (if (nil? val) nil (Boolean. (str val))))

(defn safe-to-destination [^Session s ^String queue-name]
  (if (nil? queue-name) nil (.createQueue s queue-name)))

(defn safe-to-str [val]
  (if (nil? val) nil (str val)))

(defn convert-message [^TextMessage msg]
  (if (some? msg)
    (let [^String text (.getText msg)
          ^String text2 (if (nil? text) "" text)
          text-len (.length text2)
          res {:id               (.getJMSMessageID msg)
               :jmsMessageId     (.getJMSMessageID msg)
               :type             :string
               :short-title      (subs text2 0 (min 100 text-len))
               :long-title       (subs text2 0 (min 8000 text-len))
               :size             text-len
               :jmsCorrelationId (.getJMSCorrelationID msg)
               :jmsExpiration    (.getJMSExpiration msg)
               :jmsPriority      (.getJMSPriority msg)
               :jmsTimestamp     (safe-to-str (.getJMSTimestamp msg))
               :jmsReplyTo       (safe-destination-to-str (.getJMSReplyTo msg))
               :headers          (into [] (for [property-name (enumeration-seq (.getPropertyNames msg))]
                                            {:name  property-name
                                             :value (.getStringProperty msg property-name)
                                             :type  :string}))}]
      ;(println "message! " res)
      res)))

(defn safe-call [fn convert-fn param]
  (if (some? param) (fn (convert-fn param))))

(defn convert-message-to-mq [^Session s message]
  (let [^TextMessage mq-message (.createTextMessage s)]
    (.setJMSCorrelationID mq-message (:jmsCorrelationId message))
    (safe-call #(.setJMSExpiration mq-message %) safe-to-long (:jmsExpiration message))
    (safe-call #(.setJMSPriority mq-message %) safe-to-int (:jmsPriority message))
    (safe-call #(.setJMSReplyTo mq-message %) #(safe-to-destination s %) (:jmsReplyTo message))
    (doall (for [header (:headers message)]
             (let [name (:name header)
                   value (:value header)
                   type (:type header)
                   ]
               (if (and (some? name) (some? value))
                 (case type
                   :string (.setStringProperty mq-message name value)
                   :long (.setLongProperty mq-message name (safe-to-long value))
                   :int (.setIntProperty mq-message name (safe-to-int value))
                   :short (.setShortProperty mq-message name (safe-to-short value))
                   :double (.setDoubleProperty mq-message name (safe-to-double value))
                   :float (.setShortProperty mq-message name (safe-to-float value))
                   :boolean (.setShortProperty mq-message name (safe-to-boolean value)))))))
    (.setText mq-message (:long-title message))
    mq-message))

(defn ^Message get-message [connection-info queue-info]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^MessageConsumer consumer (.createConsumer s q)
        ^Message msg (.receive consumer 1000)]
    (.close consumer)
    (.close s)
    msg))

(defn browse-queue [connection-info queue-info]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^QueueBrowser browser (.createBrowser s q)
        ^Enumeration msgs-e (.getEnumeration browser)
        messages (enumeration-seq msgs-e)
        converted-messages (doall (for [msg messages]
                                    (convert-message msg)
                                    ))
        ]
    (.close browser)
    (.close s)
    converted-messages))

(defn send-messages [connection-info queue-info messages]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^MessageProducer producer (.createProducer s q)]
    (doall (for [message messages
                 :let [mq-message (convert-message-to-mq s message)]]
             (.send producer q mq-message)))
    (.close producer)
    (.close s)))

(defn get-all [^MessageConsumer consumer]
  (let [^Message msg (.receive consumer 10)]
    ;(println "message : " msg)
    (if (some? msg)
      (recur consumer))))

(defn purge-queue [connection-info queue-info]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^MessageConsumer consumer (.createConsumer s q)]
    (get-all consumer)
    (.close consumer)
    (.close s)))
