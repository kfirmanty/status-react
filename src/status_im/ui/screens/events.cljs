(ns status-im.ui.screens.events
  (:require status-im.events
            status-im.chat.events
            status-im.dev-server.events
            status-im.network.events
            status-im.protocol.handlers
            status-im.ui.screens.accounts.login.events
            status-im.ui.screens.accounts.recover.events
            [status-im.models.contacts :as models.contacts]
            status-im.ui.screens.add-new.events
            status-im.ui.screens.add-new.new-chat.events
            status-im.ui.screens.group.chat-settings.events
            status-im.ui.screens.group.events
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.dimensions :as dimensions]
            status-im.ui.screens.accounts.events
            status-im.utils.universal-links.events
            status-im.init.events
            status-im.node.events
            status-im.signals.events
            status-im.web3.events
            status-im.notifications.events
            status-im.ui.screens.add-new.new-chat.navigation
            status-im.ui.screens.network-settings.events
            status-im.ui.screens.profile.events
            status-im.ui.screens.qr-scanner.events
            status-im.ui.screens.extensions.events
            status-im.ui.screens.wallet.events
            status-im.ui.screens.wallet.collectibles.events
            status-im.ui.screens.wallet.send.events
            status-im.ui.screens.wallet.request.events
            status-im.ui.screens.wallet.settings.events
            status-im.ui.screens.wallet.transactions.events
            status-im.ui.screens.wallet.choose-recipient.events
            status-im.ui.screens.wallet.collectibles.cryptokitties.events
            status-im.ui.screens.wallet.collectibles.cryptostrikers.events
            status-im.ui.screens.wallet.collectibles.etheremon.events
            status-im.ui.screens.wallet.collectibles.superrare.events
            status-im.ui.screens.browser.events
            status-im.ui.screens.offline-messaging-settings.events
            status-im.ui.screens.log-level-settings.events
            status-im.ui.screens.privacy-policy.events
            status-im.ui.screens.bootnodes-settings.events
            status-im.ui.screens.currency-settings.events
            status-im.utils.keychain.events
            [re-frame.core :as re-frame]
            [status-im.native-module.core :as status]
            [status-im.ui.components.permissions :as permissions]
            [status-im.transport.core :as transport]
            [status-im.transport.inbox :as inbox]
            [status-im.ui.screens.db :refer [app-db]]
            [status-im.utils.datetime :as time]
            [status-im.utils.random :as random]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.utils.http :as http]
            [status-im.utils.utils :as utils]))

(defn- http-get [{:keys [url response-validator success-event-creator failure-event-creator timeout-ms]}]
  (let [on-success #(re-frame/dispatch (success-event-creator %))
        on-error   #(re-frame/dispatch (failure-event-creator %))
        opts       {:valid-response? response-validator
                    :timeout-ms      timeout-ms}]
    (http/get url on-success on-error opts)))

(re-frame/reg-fx
 :http-get
 http-get)

(re-frame/reg-fx
 :http-get-n
 (fn [calls]
   (doseq [call calls]
     (http-get call))))

(defn- http-post [{:keys [url data response-validator success-event-creator failure-event-creator timeout-ms opts]}]
  (let [on-success #(re-frame/dispatch (success-event-creator %))
        on-error   #(re-frame/dispatch (failure-event-creator %))
        all-opts   (assoc opts
                          :valid-response? response-validator
                          :timeout-ms      timeout-ms)]
    (http/post url data on-success on-error all-opts)))

(re-frame/reg-fx
 :http-post
 http-post)

(re-frame/reg-fx
 :request-permissions-fx
 (fn [options]
   (permissions/request-permissions options)))

(re-frame/reg-fx
 :ui/listen-to-window-dimensions-change
 (fn []
   (dimensions/add-event-listener)))

(re-frame/reg-fx
 :ui/show-error
 (fn [content]
   (utils/show-popup "Error" content)))

(re-frame/reg-fx
 :ui/show-confirmation
 (fn [{:keys [title content confirm-button-text on-accept on-cancel]}]
   (utils/show-confirmation title content confirm-button-text on-accept on-cancel)))

(re-frame/reg-fx
 :ui/close-application
 (fn [_]
   (status/close-application)))

(re-frame/reg-fx
 ::app-state-change-fx
 (fn [state]
   (status/app-state-change state)))
