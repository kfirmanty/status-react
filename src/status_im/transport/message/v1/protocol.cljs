(ns ^{:doc "Protocol API and protocol utils"}
 status-im.transport.message.v1.protocol
  (:require [status-im.utils.config :as config]
            [status-im.constants :as constants]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.utils.config :as config]
            [status-im.chat.core :as chat]
            [status-im.transport.db :as transport.db]
            [status-im.transport.message.core :as message]
            [status-im.transport.utils :as transport.utils]
            [status-im.utils.fx :as fx]))

(def ^:private whisper-opts
  {:ttl       10 ;; ttl of 10 sec
   :powTarget config/pow-target
   :powTime   config/pow-time})

(fx/defn init-chat
  "Initialises chat on protocol layer.
  If topic is not passed as argument it is derived from `chat-id`"
  [{:keys [db]}
   {:keys [chat-id topic resend?]
    :or   {topic   (transport.utils/get-topic chat-id)}}]
  {:db (assoc-in db
                 [:transport/chats chat-id]
                 (transport.db/create-chat {:topic   topic
                                            :resend? resend?}))})

(fx/defn send
  "Sends the payload using symetric key and topic from db (looked up by `chat-id`)"
  [{:keys [db] :as cofx} {:keys [payload chat-id success-event]}]
  ;; we assume that the chat contains the contact public-key
  (let [{:keys [current-public-key web3]} db
        {:keys [sym-key-id topic]} (get-in db [:transport/chats chat-id])]
    {:shh/post [{:web3          web3
                 :success-event success-event
                 :message       (merge {:sig      current-public-key
                                        :symKeyID sym-key-id
                                        :payload  payload
                                        :topic    topic}
                                       whisper-opts)}]}))

(defn send-direct-message
  "Sends the payload using to dst"
  [dst success-event payload {:keys [db] :as cofx}]
  (let [{:keys [current-public-key web3]} db]
    {:shh/send-direct-message [{:web3 web3
                                :success-event success-event
                                :src     current-public-key
                                :dst     dst
                                :payload payload}]}))

(defn send-public-message
  "Sends the payload to topic"
  [chat-id success-event payload {:keys [db] :as cofx}]
  (let [{:keys [current-public-key web3]} db]
    {:shh/send-public-message [{:web3 web3
                                :success-event success-event
                                :src     current-public-key
                                :chat    chat-id
                                :payload payload}]}))
(defn send-direct-messages
  [dsts success-event payload {:keys [db] :as cofx}]
  (let [{:keys [current-public-key web3]} db]
    {:shh/send-direct-message
     (mapv #(hash-map
             :web3 web3
             :success-event success-event
             :src     current-public-key
             :dst     %
             :payload payload) dsts)}))

(fx/defn send-with-pubkey
  "Sends the payload using asymetric key (`:current-public-key` in db) and fixed discovery topic"
  [{:keys [db] :as cofx} {:keys [payload chat-id success-event]}]
  (let [{:keys [current-public-key web3]} db]
    {:shh/post [{:web3          web3
                 :success-event success-event
                 :message       (merge {:sig     current-public-key
                                        :pubKey  chat-id
                                        :payload payload
                                        :topic   (transport.utils/get-topic constants/contact-discovery)}
                                       whisper-opts)}]}))

(defn- prepare-recipients [public-keys db]
  (map (fn [public-key]
         (select-keys (get-in db [:transport/chats public-key]) [:topic :sym-key-id]))
       public-keys))

(fx/defn multi-send-by-pubkey
  "Sends payload to multiple participants selected by `:public-keys` key. "
  [{:keys [payload public-keys success-event]} {:keys [db] :as cofx}]

  (if config/encryption-enabled?
    (send-direct-messages
     cofx
     public-keys
     success-event
     payload)
    (let [{:keys [current-public-key web3]} db
          recipients                        (prepare-recipients public-keys db)]
      {:shh/multi-post {:web3          web3
                        :success-event success-event
                        :recipients    recipients
                        :message       (merge {:sig     current-public-key
                                               :payload payload}
                                              whisper-opts)}})))
;; TODO currently not used
(defrecord Ack [message-ids]
  message/StatusMessage
  (send [this cofx chat-id])
  (receive [this chat-id sig timestamp cofx]))

(defrecord Seen [message-ids]
  message/StatusMessage
  (send [this cofx chat-id])
  (receive [this chat-id sig timestamp cofx]))

(defrecord Message [content content-type message-type clock-value timestamp]
  message/StatusMessage
  (send [this chat-id cofx]
    (let [params     {:chat-id       chat-id
                      :payload       this
                      :success-event [:transport/set-message-envelope-hash
                                      chat-id
                                      (transport.utils/message-id this)
                                      message-type]}]
      (if config/encryption-enabled?
        (case (:message-type this)
          :public-group-user-message
          (send-public-message
           chat-id
           (:success-event params)
           this
           cofx)

          :user-message
          (send-direct-message
           chat-id
           (:success-event params)
           this
           cofx))
        (send-with-pubkey cofx params))))
  (receive [this chat-id signature _ cofx]
    {:chat-received-message/add-fx
     [(assoc (into {} this)
             :message-id (transport.utils/message-id this)
             :show?      true
             :chat-id    chat-id
             :from       signature
             :js-obj     (:js-obj cofx))]}))

(defrecord MessagesSeen [message-ids]
  message/StatusMessage
  (send [this chat-id cofx]
    (if config/encryption-enabled?
      (send-direct-message
       chat-id
       nil
       this
       cofx)
      (send cofx {:chat-id chat-id
             :payload this})))
  (receive [this chat-id signature _ cofx]
    (chat/receive-seen cofx chat-id signature this)))
