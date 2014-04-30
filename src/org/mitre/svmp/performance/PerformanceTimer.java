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

import android.content.Context;
import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.apprtc.AppRTCClient;
import org.mitre.svmp.common.DatabaseHandler;
import org.mitre.svmp.common.Utility;
import org.mitre.svmp.client.R;

import java.util.Date;
import java.util.Timer;

/**
 * @author Joe Portner
 * Runs tasks on intervals to gather and record performance data
 * Runs all timer tasks on a single background thread
 * Runs CPU measurement task on a separate thread
 */
public class PerformanceTimer extends Timer {
    // common variables
    private Context context;
    private AppRTCClient binder;
    private int connectionID;
    private boolean active;

    // objects that record performance measurements
    private SpanPerformanceData spanPerformanceData;
    private PointPerformanceData pointPerformanceData;

    // threads/tasks that take performance measurements
    private MeasureCpuThread measureCpuThread;
    private MeasureTask measureTask;
    private PingTask pingTask;

    public PerformanceTimer(Context context, AppRTCClient binder, int connectionID) {
        this.context = context;
        this.binder = binder;
        this.connectionID = connectionID;

        // find out if we should take measurements (set in Preferences)
        this.active = Utility.getPrefBool(context,
                R.string.preferenceKey_performance_takeMeasurements,
                R.string.preferenceValue_performance_takeMeasurements);

        // create objects to record performance measurements
        this.spanPerformanceData = new SpanPerformanceData();
        this.pointPerformanceData = new PointPerformanceData();
    }

    // getters
    public SpanPerformanceData getSpanPerformanceData() {
        return spanPerformanceData;
    }

    public PointPerformanceData getPointPerformanceData() {
        return pointPerformanceData;
    }

    // called when connection handshaking is complete and state is RUNNING
    public void start() {
        if (active) {
            // find out how often to take measurements (set in Preferences)
            int pingInterval = Utility.getPrefInt(context,
                    R.string.preferenceKey_performance_pingInterval,
                    R.string.preferenceValue_performance_pingInterval);
            int measureInterval = Utility.getPrefInt(context,
                    R.string.preferenceKey_performance_measureInterval,
                    R.string.preferenceValue_performance_measureInterval);

            // find the current time
            long startDate = System.currentTimeMillis();

            // add the current time, connection ID, and measure interval to the database (MeasurementInfo table)
            DatabaseHandler databaseHandler = new DatabaseHandler(context);
            databaseHandler.insertMeasurementInfo(
                    new MeasurementInfo(new Date(startDate), connectionID, measureInterval, pingInterval));
            databaseHandler.close();

            // create a MeasureCpuThread and run it on an interval (start immediately)
            measureCpuThread = new MeasureCpuThread(pointPerformanceData, context.getPackageName());
            measureCpuThread.start();

            // create a PingTask and run it on an interval (start immediately)
            pingTask = new PingTask(binder);
            scheduleAtFixedRate(this.pingTask, 0, pingInterval);

            // create a MeasureTask and run it on an interval
            measureTask = new MeasureTask(context, spanPerformanceData, pointPerformanceData, startDate);
            scheduleAtFixedRate(this.measureTask, measureInterval, measureInterval);
        }
        else
            cancel();
    }

    @Override
    public void cancel() {
        if (active) {
            if (measureCpuThread != null)
                measureCpuThread.cancel();
            if (pingTask != null)
                pingTask.cancel();
            if (measureTask != null)
                measureTask.cancel();
        }
        measureCpuThread = null;
        pingTask = null;
        measureTask = null;
        super.cancel();
    }
}
