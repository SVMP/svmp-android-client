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

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author Joe Portner
 */
public class MeasureCpuThread extends Thread {
    private static final String TAG = MeasureCpuThread.class.getName();

    private boolean cancelled;
    private PointPerformanceData pointPerformanceData;
    private String packageName;

    public MeasureCpuThread(PointPerformanceData pointPerformanceData, String packageName) {
        this.pointPerformanceData = pointPerformanceData;
        this.packageName = packageName;
    }

    public void run() {
        // executing the "top" program takes a few seconds, so we run this on its own thread
        while (!cancelled) {
            pointPerformanceData.setCpuUsage(getCpuUsage());
        }
    }

    public void cancel() {
        cancelled = true;
    }

    private double getCpuUsage() {
        double cpuUsage = -1;
        String topOutput = executeTop();

        if (topOutput.length() > 0) {
            // note, whitespace in topOutput varies greatly, we need to do some splits to get the CPU usage
            // topOutput: " 10414  0  31% S    32 350368K  57072K  fg u0_a37   org.mitre.svmp.client"
            String[] split1 = topOutput.split("%");
            if (split1.length > 0) {
                // we want the first element in split1
                // split1: "10414  0  31", ...
                String[] split2 = split1[0].split(" ");
                if (split2.length > 0) {
                    // we want the last element in split2
                    // split2: ..., "31"
                    try {
                        // parse the number and convert it to a double (0.0 to 1.0)
                        int cpuUsageInt = Integer.parseInt(split2[split2.length-1]);
                        cpuUsage = (double)cpuUsageInt / 100.0;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "getCpuUsage failed parsing number: " + e.getMessage());
                    }
                }
            }
        }

        return cpuUsage;
    }

    private String executeTop() {
        Process p = null;
        BufferedReader in = null;
        String returnString = "",
                readString;
        try {
            p = Runtime.getRuntime().exec("top -n 1");
            in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((readString = in.readLine()) != null) {
                if (readString.endsWith(packageName)) {
                    returnString = readString;
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "error in getting first line of top: " + e.getMessage());
        } finally {
            if (in != null)
                try {
                    in.close();
                    p.destroy();
                } catch (IOException e) {
                    Log.e(TAG, "error in closing and destroying top process: " + e.getMessage());
                }
        }
        return returnString;
    }
}
