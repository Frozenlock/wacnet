(ns wacnet.bacnet-utils
  (:require [bacure.core :as bac]))

(defn first-boot []
  (bac/new-local-device)
  (bac/initialize)
  (future (bac/find-remote-devices-and-extended-information)))

(defn reboot []
  (bac/reset-local-device)
  (future (bac/find-remote-devices-and-extended-information)))