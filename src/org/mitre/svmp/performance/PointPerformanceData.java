/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp.performance;

import android.telephony.TelephonyManager;

/**
 * @author Joe Portner
 * This object keeps track of performance measurements that are taken at a single point in time
 */
public class PointPerformanceData {
    private double cpuUsage = -1; // % (0.0 to 1.0) or -1 (unknown)
    private int memoryUsage; // kB
    private double batteryLevel = -1; // % (0.0 to 1.0) or -1 (unknown)
    private double wifiStrength = -1; // % (0.0 to 1.0) or -1 (unknown)
    private int cellNetwork = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private String cellValues = ""; // varies
    private int ping; // ms
    private long lastPingDate; // last time the ping value was set

    // constructor
    public PointPerformanceData() {}

    // getters
    public double getCpuUsage() {
        return cpuUsage;
    }

    public int getMemoryUsage() {
        return memoryUsage;
    }

    public double getWifiStrength() {
        return wifiStrength;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public int getCellNetwork() {
        return cellNetwork;
    }

    public String getCellValues() {
        return cellValues;
    }

    public int getPing() {
        return ping;
    }

    // setters
    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public void setMemoryUsage(int memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public void setWifiStrength(double wifiStrength) {
        this.wifiStrength = wifiStrength;
    }

    public synchronized void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public synchronized void setCellData(int cellNetwork, String cellValues) {
        this.cellNetwork = cellNetwork;
        this.cellValues = cellValues;
    }

    public synchronized void setPing(long startDate, long endDate) {
        if (endDate > startDate && endDate > lastPingDate) {
            this.ping = (int)(endDate - startDate);
            this.lastPingDate = endDate;
        }
    }

    public String toString() {
        return String.format("cpuUsage '%s', memoryUsage '%dkB', wifiStrength '%s', batteryLevel '%s', cellNetwork '%s', cellValues '%s', ping '%sms'",
                cpuUsage, memoryUsage, wifiStrength, batteryLevel, cellNetwork, cellValues, ping);
    }
}
