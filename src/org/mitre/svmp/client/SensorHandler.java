/*
 * Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.svmp.client;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import org.mitre.svmp.apprtc.AppRTCClient;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.Utility;
import org.mitre.svmp.performance.PerformanceAdapter;
import org.mitre.svmp.performance.SpanPerformanceData;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.SensorType;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import org.mitre.svmp.services.SessionService;

public class SensorHandler implements SensorEventListener, Constants {
    private static final String TAG = SensorHandler.class.getName();

    private SessionService service;
    private PerformanceAdapter performanceAdapter;
    private SensorManager sm;
    
    private List<Sensor> registeredSensors = new ArrayList<Sensor>(PREFERENCES_SENSORS_KEYS.length);
    
    // track timestamp of the last update of each sensor we are tracking
    private long[] lastSensorUpdate = new long[PREFERENCES_SENSORS_KEYS.length + 1];

    // minimum allowed time between sensor updates in nanoseconds
    private long minimumSensorDelay;
    
    public SensorHandler(SessionService service, PerformanceAdapter performanceAdapter) {
        this.service = service;
        this.performanceAdapter = performanceAdapter;
        this.sm = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
        
        // get preferences to determine which sensors we should listen to
        this.minimumSensorDelay = Utility.getPrefInt(service,
                R.string.preferenceKey_sensors_minimumDelay, R.string.preferenceValue_sensors_minimumDelay);
        this.minimumSensorDelay *=  1000; // convert microseconds to nanoseconds
    }

    public void initSensors() {
        Log.d(TAG, "startClient started registering listener");

        // get preferences to determine which sensors we should listen to
        // loop through preferences...
        for( int i = 0; i < PREFERENCES_SENSORS_KEYS.length; i++ ) {
            // if this sensor is enabled in the preferences, register a listener for it
            if( Utility.getPrefBool(service, PREFERENCES_SENSORS_KEYS[i], PREFERENCES_SENSORS_DEFAULTVALUES[i]) )
                initSensor(i+1); // sensors start at 1, not 0
        }
    }
    
    public void cleanupSensors() {
        Log.d(TAG,"cleanupsensors()");

        for(Sensor currentSensor : registeredSensors) {
            sm.unregisterListener(this,currentSensor);
        }
   }
    
    private boolean initSensor(int type) {
        Sensor s = sm.getDefaultSensor(type);
        if (s != null) {
            Log.i(TAG, "Registering for sensor: (type " + s.getType() + ") " + s.getVendor() + " " + s.getName());
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
            registeredSensors.add(s);
            return true;
        } else {
            Log.e(TAG, "Failed registering listener for default sensor of type " + type);
            return false;
        }
    }
    
    private long getScaledMinDelay(int index) {
        long delay = minimumSensorDelay;

        if( SENSOR_MINIMUM_UPDATE_SCALES.length > index && index >= 0 )
            delay *= index;

        return delay;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        // make sure the time is past the minimum sensor delay, prevents spammy sensor messages
        if (event.timestamp >= lastSensorUpdate[type] + getScaledMinDelay(type-1)) {
            lastSensorUpdate[type] = event.timestamp;

            // increment the sensor update count for performance measurement
            performanceAdapter.incrementSensorUpdates();

            // send the sensor request message
            service.sendMessage(makeSensorRequest(event));
        }
    }

    private Request makeSensorRequest(SensorEvent event) {
        // assemble the message
        SVMPProtocol.SensorEvent.Builder e = SVMPProtocol.SensorEvent.newBuilder();
        e.setType(SensorType.valueOf(event.sensor.getType()));
        e.setAccuracy(event.accuracy);
        e.setTimestamp(event.timestamp);

        List<Float> vals = new ArrayList<Float>(event.values.length);
        for (float v : event.values) vals.add(v);
            e.addAllValues(vals);

        return Request.newBuilder()
                .setType(RequestType.SENSOREVENT)
                .addSensor(e) // TODO: batch sensor events
                .build();
    }
}
