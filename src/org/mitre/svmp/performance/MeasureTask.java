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

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Debug.MemoryInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import org.mitre.svmp.common.DatabaseHandler;

import java.util.TimerTask;

/**
 * @author Joe Portner
 * Runs on an interval to gather performance data and write it to the database
 * Controlled by PerformanceTimer
 */
public class MeasureTask extends TimerTask {
    private static final String TAG = MeasureTask.class.getName();

    private boolean running = true;
    private Context context;
    private SpanPerformanceData spanPerformanceData;
    private final PointPerformanceData pointPerformanceData;
    private long startDate;

    private DatabaseHandler databaseHandler; // used to record values to database
    private ActivityManager activityManager; // used to get memory usage
    private WifiManager wifiManager; // used to get wifi strength
    private TelephonyManager telephonyManager; // used to get cell signal values
    private int phoneType; // PHONE_TYPE_NONE, PHONE_TYPE_GSM, PHONE_TYPE_CDMA

    public MeasureTask(Context context, SpanPerformanceData spanPerformanceData, PointPerformanceData pointPerformanceData, long startDate) {
        this.context = context;
        this.spanPerformanceData = spanPerformanceData;
        this.pointPerformanceData = pointPerformanceData;
        this.startDate = startDate;

        this.databaseHandler = new DatabaseHandler(context);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        context.registerReceiver(this.batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        phoneType = telephonyManager.getPhoneType();
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM || phoneType == TelephonyManager.PHONE_TYPE_CDMA)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public void run() {
        // create a copy of the measurement data, and reset the values for the original object
        SpanPerformanceData spanMeasurements = spanPerformanceData.reset();

        int memoryUsage = getMemoryUsage();
        double wifiStrength = getWifiStrength();

        // take single-point-in-time measurements, then add them
        synchronized (pointPerformanceData) {
            // cpu usage is set by the MeasureCpuThread
            pointPerformanceData.setMemoryUsage(memoryUsage); // set memory used
            pointPerformanceData.setWifiStrength(wifiStrength); // set wifi strength
            // battery level is set by the batteryInfoReceiver
            // cell signal values are set by the phoneStateListener
            // ping value is set by the AppRTCActivity

            if (running)
                databaseHandler.insertPerformanceData(startDate, spanMeasurements, pointPerformanceData);

            Log.d(TAG, String.format("[%s, %s]", spanMeasurements, pointPerformanceData));
        }
    }

    @Override
    public boolean cancel() {
        try {
            running = false;
            context.unregisterReceiver(batteryInfoReceiver);
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE); // unregister listener
            databaseHandler.close();
        } catch (Exception e) {
            // don't care
        }
        return super.cancel();
    }

    // returns memory used in kB (using Debug MemoryInfo, *not* ActivityManager MemoryInfo)
    private int getMemoryUsage() {
        MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
        // "Pss" is scaled, and it's probably the best thing to measure here; consider "PrivateDirty" too?
        // see http://stackoverflow.com/questions/2298208/how-to-discover-memory-usage-of-my-application-in-android
        return memoryInfoArray[0].dalvikPss; // "proportional set size", an estimate of how much memory the app is using
        // dalvikPrivateDirty: the memory that would be freed by the java virtual machine if the process is killed
        // dalvikSharedDirty: the shared memory used by the java virtual machine, not freed if the process is killed
    }

    // returns wifi strength % (0.0 to 1.0)
    private double getWifiStrength() {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int wifiStrengthInt = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 101); // wifi strength, 0 to 100
        return ((double)wifiStrengthInt) / 100.0;
    }

    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                synchronized (pointPerformanceData) {
                    int rawlevel = intent.getIntExtra("level", -1);
                    double scale = intent.getIntExtra("scale", -1);
                    double level = -1;
                    if (rawlevel >= 0 && scale > 0)
                        level = rawlevel / scale;
                    pointPerformanceData.setBatteryLevel(level);
                }
            }
        }
    };

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            int cellNetwork = telephonyManager.getNetworkType();
            StringBuilder cellValuesBuilder = new StringBuilder(); // fastest way to concatenate strings

            // get the cell signal values via string
            String signalStrengthString = signalStrength.toString();
            String[] parts = signalStrengthString.split(" ");
            /* 2.3.7: --------------------------------------------------------------------------------------------------
             *   [1]                [2]              [3]      [4]       [5]      [6]       [7]      [8]
             *   GsmSignalStrength, GsmBitErrorRate, CdmaDbm, CdmaEcio, EvdoDbm, EvdoEcio, EvdoSnr, (isGsm?"gsm":"cdma")
             * 4.0.3: --------------------------------------------------------------------------------------------------
             *   [1]                [2]              [3]      [4]       [5]      [6]       [7]      [8]
             *   GsmSignalStrength, GsmBitErrorRate, CdmaDbm, CdmaEcio, EvdoDbm, EvdoEcio, EvdoSnr, LteSignalStrength,
             *   [9]      [10]     [11]      [12]    [13]
             *   LteRsrp, LteRsrq, LteRssnr, LteCqi, (isGsm?"gsm|lte":"cdma"));
             */

            // measuring LTE signal strength wasn't added until API 17; this is a different way to do it with an older API
            if (cellNetwork == 13 /* NETWORK_TYPE_LTE */) {
                if (parts.length >= 14) {
                    cellValuesBuilder.append(parts[8]); // LteSignalStrength
                    cellValuesBuilder.append(" ");
                    cellValuesBuilder.append(parts[9]); // LteRsrp
                    cellValuesBuilder.append(" ");
                    cellValuesBuilder.append(parts[10]); // LteRsrq
                    cellValuesBuilder.append(" ");
                    cellValuesBuilder.append(parts[11]); // LteRssnr
                    cellValuesBuilder.append(" ");
                    cellValuesBuilder.append(parts[12]); // LteCqi
                }
            }
            // there are lots of different network types that make up GSM; use phone type instead
            else if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                /* Taken from "3GPP TS 27.007", v6.3.0 document:
                 *   GsmSignalStrength    dBm
                 *   0                    -113 or less (worst)
                 *   1                    -111
                 *   2 ... 30             -109 ... -53
                 *   31                   -51 (best)
                 *   99                   (unknown)
                 */
                int cellDbm = 0;
                int gsmSignalStrength = signalStrength.getGsmSignalStrength();
                if (gsmSignalStrength != 99)
                    cellDbm = -113 + (gsmSignalStrength * 2); // dBm (higher is better)

                cellValuesBuilder.append(cellDbm); // calculated GSM dBm: 0 (unknown) OR -131 (worst) to -51 (best)
                cellValuesBuilder.append(" ");
                cellValuesBuilder.append(parts[1]); // GsmSignalStrength: 99 (unknown) OR 0 (worst) to 31 (best)
                cellValuesBuilder.append(" ");
                cellValuesBuilder.append(parts[2]); // GsmBitErrorRate: 99 (unknown) OR 0 (best) to 7 (worst)
            }
            // there are lots of different network types that make up CDMA; use phone type instead
            else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                switch (cellNetwork) {
                    // three network types are used for EVDO
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case 12 /* NETWORK_TYPE_EVDO_B */:
                        cellValuesBuilder.append(parts[5]); // EVDO dBm (higher is better)
                        cellValuesBuilder.append(" ");
                        cellValuesBuilder.append(parts[6]); // EVDO Ec/Io dB*10 (higher is better)
                        break;
                    // any other network types are CDMA
                    default:
                        cellValuesBuilder.append(parts[3]); // CDMA dBm (higher is better)
                        cellValuesBuilder.append(" ");
                        cellValuesBuilder.append(parts[4]); // CDMA Ec/Io, dB*10 (higher is better)
                        break;
                }
                cellValuesBuilder.append(" ");
                cellValuesBuilder.append(parts[7]); // SNR: -1 (unknown) OR 0 (worst/no signal) to 8 (best)
            }

            String cellValues = cellValuesBuilder.toString();
            pointPerformanceData.setCellData(cellNetwork, cellValues);
        }
    };
}
