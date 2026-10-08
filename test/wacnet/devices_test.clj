(ns wacnet.devices-test
  (:require [bacure.core :as b]
            [bacure.remote-device :as rd]
            [clojure.test :refer :all]
            [wacnet.api.bacnet.devices :as devices]))

(deftest device-summary-fetches-the-identity
  ;; Opening a device is an explicit request: its identity must be
  ;; fetched (and thus cached, for the devices list) even if the
  ;; logger's filter excludes the device.
  (let [fetched (atom [])]
    (with-redefs [rd/extended-information (fn [_ device-id] (swap! fetched conj device-id) {})
                  rd/routing-info (constantly {:address {:mac-address "10.0.0.5:47808"}})
                  b/remote-object-properties (fn [& _] [{:object-identifier [:device 5]
                                                         :object-name "Tour C - RTU 5"
                                                         :vendor-name "ACME"}])]
      (let [summary (devices/device-summary nil 5)]
        (is (= [5] @fetched))
        (is (= "Tour C - RTU 5" (:device-name summary)))
        (is (= "5" (:device-id summary)))
        (is (not (contains? summary :object-identifier))))))
  (testing "a device that doesn't answer its identity is still shown"
    (with-redefs [rd/extended-information (fn [& _] (throw (ex-info "timeout" {})))
                  rd/routing-info (constantly {})
                  b/remote-object-properties (fn [& _] [{:object-identifier [:device 6]}])]
      (is (= "6" (:device-id (devices/device-summary nil 6)))))))
