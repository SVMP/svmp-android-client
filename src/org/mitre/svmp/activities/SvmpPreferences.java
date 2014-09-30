/*
 Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.

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
package org.mitre.svmp.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.DatabaseHandler;
import org.mitre.svmp.performance.MeasurementInfo;

import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * @author Joe Portner
 */
public class SvmpPreferences extends PreferenceActivity {
    private static final String TAG = SvmpPreferences.class.getName();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // deprecated in API 11
        addPreferencesFromResource(R.xml.preferences);

        // hide Memorizing Trust Manager if trust store is not empty
        boolean hideMTM = false;
        try {
            KeyStore localTrustStore = KeyStore.getInstance("BKS");
            InputStream in = this.getResources().openRawResource(R.raw.client_truststore);
            localTrustStore.load(in, Constants.TRUSTSTORE_PASSWORD.toCharArray());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(localTrustStore);
            hideMTM = localTrustStore.size() > 0;
        } catch (Exception e) {
            // don't care
        }
        if (hideMTM) {
            // deprecated in API 11
            Preference pref = findPreference(this.getResources().getString(R.string.preferenceKey_connection_useMTM));
            if (pref != null)
                pref.setEnabled(false);
        }
    }

    @Override
    public void finish() {
        Intent intent = new Intent();
        setResult(SvmpActivity.RESULT_REFRESHPREFS, intent);
        super.finish();
    }

    // called by button click in Performance section
    public void exportData(View view) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            toastShort(R.string.preference_performance_exportData_toast_unavailable);
        else {
            // create a dialog
            final AlertDialog dialog = new AlertDialog.Builder(SvmpPreferences.this)
                    .setTitle(R.string.preference_performance_exportData_text)
                    .setMessage(R.string.preference_performance_exportData_dialog_message)
                    .setPositiveButton(R.string.preference_performance_exportData_dialog_positiveButton,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    doExport();
                                }
                            })
                    .setNegativeButton(R.string.preference_performance_exportData_dialog_negativeButton,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Do nothing.
                                }
                            }).create();
            // show the dialog
            dialog.show();
        }
    }

    // called by button click in Performance section
    public void wipeData(View view) {
        // create a dialog
        final AlertDialog dialog = new AlertDialog.Builder(SvmpPreferences.this)
                .setTitle(R.string.preference_performance_wipeData_text)
                .setMessage(R.string.preference_performance_wipeData_dialog_message)
                .setPositiveButton(R.string.preference_performance_wipeData_dialog_positiveButton,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                doWipe();
                            }
                        })
                .setNegativeButton(R.string.preference_performance_wipeData_dialog_negativeButton,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // Do nothing.
                            }
                        }).create();
        // show the dialog
        dialog.show();
    }

    private void doExport() {
        File folder = new File(Environment.getExternalStorageDirectory(), "/svmp/performance_data/");
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Couldn't create performance data folder");
                toastShort(R.string.preference_performance_exportData_toast_fail);
                return;
            }
        }

        DatabaseHandler handler = new DatabaseHandler(this);

        List<MeasurementInfo> measurementInfoList = handler.getAllMeasurementInfo();
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int phoneTypeInt = telephonyManager.getPhoneType();
        String phoneType = "", cellValues = "";
        if (phoneTypeInt == TelephonyManager.PHONE_TYPE_GSM) {
            phoneType = "GSM";
            cellValues = "GSM[dBm SignalStrength BER]";
        } else if (phoneTypeInt == TelephonyManager.PHONE_TYPE_CDMA) {
            phoneType = "CDMA";
            cellValues = "CDMA[dBm Ec/Io SNR]";
        }
        cellValues = cellValues + " LTE[SignalStrength Rsrp Rsrq Rssnr Cqi]";

        String error = "";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ssZ");
        int filesWritten = 0;
        for (MeasurementInfo measurementInfo : measurementInfoList) {
            // get the performance data associated with this record
            List<String> performanceDataList = handler.getAllPerformanceData(measurementInfo);

            // if there is no data (the user probably connected and immediately exited), skip this record
            if (performanceDataList.size() == 0)
                continue;

            filesWritten++;
            PrintWriter out = null;

            try {
                // open a file and a PrintWriter
                File file = new File(folder, dateFormat.format(measurementInfo.getStartDate()) + ".csv");
                out = new PrintWriter(new FileWriter(file));

                // print headers
                out.println("Measure date,Frames per second,Sensor updates per second,Touch updates per second,"
                        + "CPU usage (%),Memory usage (kB),Wifi strength (%),Battery level (%),Cell network ("
                        + phoneType + "),Cell values (" + cellValues + "),Ping (ms)");
                for (String performanceData : performanceDataList)
                    out.println(performanceData);
            } catch (IOException e) {
                Log.e(TAG, "Error exporting performance data to external storage: " + e.getMessage());
                error = e.getMessage();
            } finally {
                if (out != null)
                    out.close();
            }

            // if we failed, break out of the loop
            if (error.length() > 0)
                break;
        }

        handler.close();

        if (error.length() > 0)
            toastShort(R.string.preference_performance_exportData_toast_error, " " + error);
        else if (filesWritten > 0)
            toastShort(R.string.preference_performance_exportData_toast_success,
                    " (wrote " + filesWritten + " file" + (filesWritten > 1 ? "s" : "") + ")");
        else
            toastShort(R.string.preference_performance_exportData_toast_noneWritten);
    }

    private void doWipe() {
        DatabaseHandler handler = new DatabaseHandler(this);
        handler.wipeAllPerformanceData();
        handler.close();

        toastShort(R.string.preference_performance_wipeData_toast_success);
    }

    private void toastShort(int resId) { toastShort(resId, ""); } // overload
    private void toastShort(int resId, String string) {
        String resString = getResources().getString(resId);
        Toast toast = Toast.makeText(this, resString + string, Toast.LENGTH_SHORT);
        toast.show();
    }
}