(ns wacnet.api.bacnet.mock-data
  "Mock BACnet data for testing the UI without a live network.

   Toggle at REPL:
     (reset! wacnet.api.bacnet.mock-data/use-mock-data? true)
     (reset! wacnet.api.bacnet.mock-data/use-mock-data? false)")

(defonce use-mock-data? (atom false))

(def devices
  "Mock device list with router relationships."
  [{:device-id "1000" :device-name "Main Router" :network nil}
   {:device-id "2001" :device-name "VAV Box 1" :network {:routed-by {:device-id "1000" :mac-address "192.168.1.100:47808"}}}
   {:device-id "2002" :device-name "VAV Box 2" :network {:routed-by {:device-id "1000" :mac-address "192.168.1.100:47808"}}}
   {:device-id "2003" :device-name "VAV Box 3" :network {:routed-by {:device-id "1000" :mac-address "192.168.1.100:47808"}}}
   {:device-id "1100" :device-name "Secondary Router" :network nil}
   {:device-id "3001" :device-name "AHU Controller" :network {:routed-by {:device-id "1100" :mac-address "192.168.1.101:47808"}}}
   {:device-id "3002" :device-name "Chiller Controller" :network {:routed-by {:device-id "1100" :mac-address "192.168.1.101:47808"}}}
   {:device-id "100" :device-name "BAS Server" :network nil}
   {:device-id "101" :device-name "Lighting Controller" :network nil}
   {:device-id "102" :device-name "Energy Meter" :network nil}
   ;; Devices routed by an unknown router (only mac-address, no device-id)
   {:device-id "4001" :device-name "Remote Sensor 1" :network {:routed-by {:mac-address "192.168.1.200:47808"}}}
   {:device-id "4002" :device-name "Remote Sensor 2" :network {:routed-by {:mac-address "192.168.1.200:47808"}}}
   {:device-id "4003" :device-name "Remote Actuator" :network {:routed-by {:mac-address "192.168.1.200:47808"}}}])

(def device-summaries
  "Mock device summaries with full network info."
  {"1000" {:device-name "Main Router"
           :description "Primary MS/TP router for floor 1"
           :vendor-name "Reliable Controls"
           :vendor-identifier 159
           :model-name "MACH-ProWebSys"
           :_network {:address {:network-number 0 :mac-address "192.168.1.100:47808"}
                      :routes-to [2]}}
   "2001" {:device-name "VAV Box 1"
           :description "Conference Room A"
           :vendor-name "Reliable Controls"
           :vendor-identifier 159
           :model-name "MACH-ProZone"
           :_network {:address {:network-number 2 :mac-address "10"}
                      :routed-by {:device-id 1000 :device-name "Main Router" :mac-address "192.168.1.100:47808"}}}
   "2002" {:device-name "VAV Box 2"
           :description "Conference Room B"
           :vendor-name "Reliable Controls"
           :vendor-identifier 159
           :model-name "MACH-ProZone"
           :_network {:address {:network-number 2 :mac-address "11"}
                      :routed-by {:device-id 1000 :device-name "Main Router" :mac-address "192.168.1.100:47808"}}}
   "2003" {:device-name "VAV Box 3"
           :description "Open Office Area"
           :vendor-name "Reliable Controls"
           :vendor-identifier 159
           :model-name "MACH-ProZone"
           :_network {:address {:network-number 2 :mac-address "12"}
                      :routed-by {:device-id 1000 :device-name "Main Router" :mac-address "192.168.1.100:47808"}}}
   "1100" {:device-name "Secondary Router"
           :description "Router for mechanical room"
           :vendor-name "Tridium"
           :vendor-identifier 46
           :model-name "JACE 8000"
           :_network {:address {:network-number 0 :mac-address "192.168.1.101:47808"}
                      :routes-to [3]}}
   "3001" {:device-name "AHU Controller"
           :description "Air Handling Unit 1"
           :vendor-name "Tridium"
           :vendor-identifier 46
           :model-name "JACE 8000"
           :_network {:address {:network-number 3 :mac-address "5"}
                      :routed-by {:device-id 1100 :device-name "Secondary Router" :mac-address "192.168.1.101:47808"}}}
   "3002" {:device-name "Chiller Controller"
           :description "Chiller Plant"
           :vendor-name "Tridium"
           :vendor-identifier 46
           :model-name "JACE 8000"
           :_network {:address {:network-number 3 :mac-address "6"}
                      :routed-by {:device-id 1100 :device-name "Secondary Router" :mac-address "192.168.1.101:47808"}}}
   "100"  {:device-name "BAS Server"
           :description "Building Automation Server"
           :vendor-name "Honeywell"
           :vendor-identifier 52
           :model-name "WEBs-AX"
           :_network {:address {:network-number 0 :mac-address "192.168.1.10:47808"}}}
   "101"  {:device-name "Lighting Controller"
           :description "Floor 1 Lighting"
           :vendor-name "Philips"
           :vendor-identifier 302
           :model-name "Dynalite"
           :_network {:address {:network-number 0 :mac-address "192.168.1.11:47808"}}}
   "102"  {:device-name "Energy Meter"
           :description "Main electrical meter"
           :vendor-name "Schneider Electric"
           :vendor-identifier 10
           :model-name "PowerLogic"
           :_network {:address {:network-number 0 :mac-address "192.168.1.12:47808"}}}
   ;; Devices routed by unknown router (only mac-address in routed-by)
   "4001" {:device-name "Remote Sensor 1"
           :description "Remote temperature sensor"
           :vendor-name "Unknown Vendor"
           :vendor-identifier 999
           :model-name "Sensor-Pro"
           :_network {:address {:network-number 5 :mac-address "20"}
                      :routed-by {:mac-address "192.168.1.200:47808"}}}
   "4002" {:device-name "Remote Sensor 2"
           :description "Remote humidity sensor"
           :vendor-name "Unknown Vendor"
           :vendor-identifier 999
           :model-name "Sensor-Pro"
           :_network {:address {:network-number 5 :mac-address "21"}
                      :routed-by {:mac-address "192.168.1.200:47808"}}}
   "4003" {:device-name "Remote Actuator"
           :description "Remote damper actuator"
           :vendor-name "Unknown Vendor"
           :vendor-identifier 999
           :model-name "Actuator-X"
           :_network {:address {:network-number 5 :mac-address "22"}
                      :routed-by {:mac-address "192.168.1.200:47808"}}}})

(defn objects-for-device
  "Returns mock objects for a given device-id."
  [device-id]
  (case (str device-id)
    "1000" [{:object-id "8.1000" :object-type "8" :object-instance "1000"
             :object-name "Main Router" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Zone Temp" :description "Zone temperature" :present-value 21.5 :units "degrees-celsius"}
            {:object-id "0.2" :object-type "0" :object-instance "2"
             :object-name "Outside Air Temp" :description "OAT sensor" :present-value 15.2 :units "degrees-celsius"}]
    "2001" [{:object-id "8.2001" :object-type "8" :object-instance "2001"
             :object-name "VAV Box 1" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Room Temp" :description "Room temperature" :present-value 22.1 :units "degrees-celsius"}
            {:object-id "1.1" :object-type "1" :object-instance "1"
             :object-name "Damper Position" :description "VAV damper" :present-value 45.0 :units "percent"}
            {:object-id "2.1" :object-type "2" :object-instance "1"
             :object-name "Reheat Valve" :description "Hot water valve" :present-value 20.0 :units "percent"}
            {:object-id "3.1" :object-type "3" :object-instance "1"
             :object-name "Occupancy" :description "Occupancy sensor" :present-value 1}]
    "2002" [{:object-id "8.2002" :object-type "8" :object-instance "2002"
             :object-name "VAV Box 2" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Room Temp" :description "Room temperature" :present-value 23.4 :units "degrees-celsius"}
            {:object-id "1.1" :object-type "1" :object-instance "1"
             :object-name "Damper Position" :description "VAV damper" :present-value 60.0 :units "percent"}]
    "2003" [{:object-id "8.2003" :object-type "8" :object-instance "2003"
             :object-name "VAV Box 3" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Room Temp" :description "Room temperature" :present-value 21.8 :units "degrees-celsius"}
            {:object-id "1.1" :object-type "1" :object-instance "1"
             :object-name "Damper Position" :description "VAV damper" :present-value 35.0 :units "percent"}]
    "1100" [{:object-id "8.1100" :object-type "8" :object-instance "1100"
             :object-name "Secondary Router" :description "Device object" :present-value nil}]
    "3001" [{:object-id "8.3001" :object-type "8" :object-instance "3001"
             :object-name "AHU Controller" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Supply Air Temp" :description "SAT" :present-value 18.5 :units "degrees-celsius"}
            {:object-id "0.2" :object-type "0" :object-instance "2"
             :object-name "Return Air Temp" :description "RAT" :present-value 22.0 :units "degrees-celsius"}
            {:object-id "0.3" :object-type "0" :object-instance "3"
             :object-name "Mixed Air Temp" :description "MAT" :present-value 19.5 :units "degrees-celsius"}
            {:object-id "1.1" :object-type "1" :object-instance "1"
             :object-name "Supply Fan Speed" :description "VFD output" :present-value 75.0 :units "percent"}
            {:object-id "1.2" :object-type "1" :object-instance "2"
             :object-name "OA Damper" :description "Outside air damper" :present-value 30.0 :units "percent"}
            {:object-id "4.1" :object-type "4" :object-instance "1"
             :object-name "Supply Fan Status" :description "Fan running" :present-value 1}]
    "3002" [{:object-id "8.3002" :object-type "8" :object-instance "3002"
             :object-name "Chiller Controller" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "CHWS Temp" :description "Chilled water supply" :present-value 7.2 :units "degrees-celsius"}
            {:object-id "0.2" :object-type "0" :object-instance "2"
             :object-name "CHWR Temp" :description "Chilled water return" :present-value 12.1 :units "degrees-celsius"}
            {:object-id "4.1" :object-type "4" :object-instance "1"
             :object-name "Chiller Status" :description "Chiller running" :present-value 1}]
    "100"  [{:object-id "8.100" :object-type "8" :object-instance "100"
             :object-name "BAS Server" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Server Room Temp" :description "Server room" :present-value 19.5 :units "degrees-celsius"}]
    "101"  [{:object-id "8.101" :object-type "8" :object-instance "101"
             :object-name "Lighting Controller" :description "Device object" :present-value nil}
            {:object-id "4.1" :object-type "4" :object-instance "1"
             :object-name "Zone 1 Lights" :description "Lighting status" :present-value 1}
            {:object-id "4.2" :object-type "4" :object-instance "2"
             :object-name "Zone 2 Lights" :description "Lighting status" :present-value 0}]
    "102"  [{:object-id "8.102" :object-type "8" :object-instance "102"
             :object-name "Energy Meter" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "kW Demand" :description "Power demand" :present-value 125.4 :units "kilowatts"}
            {:object-id "0.2" :object-type "0" :object-instance "2"
             :object-name "kWh Total" :description "Energy consumption" :present-value 45230.0 :units "kilowatt-hours"}]
    ;; Devices routed by unknown router
    "4001" [{:object-id "8.4001" :object-type "8" :object-instance "4001"
             :object-name "Remote Sensor 1" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Remote Temp" :description "Remote temperature" :present-value 24.3 :units "degrees-celsius"}]
    "4002" [{:object-id "8.4002" :object-type "8" :object-instance "4002"
             :object-name "Remote Sensor 2" :description "Device object" :present-value nil}
            {:object-id "0.1" :object-type "0" :object-instance "1"
             :object-name "Remote Humidity" :description "Remote humidity" :present-value 55.0 :units "percent-relative-humidity"}]
    "4003" [{:object-id "8.4003" :object-type "8" :object-instance "4003"
             :object-name "Remote Actuator" :description "Device object" :present-value nil}
            {:object-id "1.1" :object-type "1" :object-instance "1"
             :object-name "Damper Cmd" :description "Damper command" :present-value 50.0 :units "percent"}]
    ;; Default empty list for unknown devices
    []))
