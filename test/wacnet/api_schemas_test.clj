(ns wacnet.api-schemas-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [wacnet.api.bacnet.local-device :as api-ld]
            [wacnet.api.vigilia-logger :as api-vl]))

(deftest logger-configs-schema
  (is (nil? (s/check api-vl/Configs {:time-interval 10 :discovery-interval 0 :id-to-remove [1 2]})))
  (is (nil? (s/check api-vl/Configs {})))
  (is (some? (s/check api-vl/Configs {:discovery-interval "60"})) "integers only"))

(deftest bacnet-configs-schema
  (is (nil? (s/check api-ld/LocalConfigs {:object-name "Tour C"
                                          :apdu-timeout 6000
                                          :apdu-segment-timeout 5000
                                          :number-of-apdu-retries 1
                                          :extended-information-retry-ms 300000
                                          :extended-information-max-retry-ms 900000})))
  (testing "every key accepted by the API is also returned by it"
    (is (empty? (remove api-ld/allowed-configs-keys
                        (map s/explicit-schema-key (keys api-ld/LocalConfigs)))))))
