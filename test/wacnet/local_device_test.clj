(ns wacnet.local-device-test
  (:require [clojure.test :refer :all]
            [vigilia-logger.timed :as timed]
            [wacnet.api.bacnet.local-device :as api-ld]
            [wacnet.local-device :as wld]))

(deftest boot-up-configs
  (testing "defaults"
    (let [configs (wld/boot-up-configs nil)]
      (is (= "Wacnet webserver" (:object-name configs)))
      (is (= 697 (:vendor-identifier configs)))
      (is (string? (:description configs)))))
  (testing "name and description from the saved configs"
    (let [configs (wld/boot-up-configs {:object-name "Tour C" :description "Logger tour C"})]
      (is (= "Tour C" (:object-name configs)))
      (is (= "Logger tour C" (:description configs))))))

(deftest every-boot-up-installs-the-logger-filter
  ;; The filter must be in place before bacure starts its network
  ;; discovery: it has to be given to boot-up! itself, on every path
  ;; that boots the local device.
  (let [calls (atom [])]
    (with-redefs [bacure.core/boot-up! (fn [& args] (swap! calls conj args) 1234)
                  bacure.local-device/local-device-backup (constantly {:device-id 1234})
                  bacure.local-device/clear-all! (constantly nil)
                  bacure.local-device/save-local-device-backup! (constantly {:device-id 1234})]
      (testing "startup"
        (wld/boot-up!)
        (let [[configs runtime-options] (last @calls)]
          (is (= "Wacnet" (:model-name configs)))
          (is (= timed/auto-fetch-filter (:auto-fetch-filter runtime-options)))))
      (testing "BACnet settings update"
        (api-ld/reset-and-save! {:port 47809})
        (let [[configs runtime-options] (last @calls)]
          (is (= 47809 (:port configs)))
          (is (= 1234 (:device-id configs)) "merged with the current configs")
          (is (= timed/auto-fetch-filter (:auto-fetch-filter runtime-options))))))
    (is (= 2 (count @calls)))))
