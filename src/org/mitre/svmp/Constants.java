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
package org.mitre.svmp;

import android.os.Build;
import org.mitre.svmp.client.R;

/**
 * @author Joe Portner
 */
public interface Constants {
    public static final int DEFAULT_PORT = 8002;
    public static final boolean API_ICS = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH);

    // used to determine what the EncryptionType for each connection is
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_SSLTLS = 1;
    public static final int ENCRYPTION_SSLTLS_UNTRUSTED = 2;

    // used to map sensor IDs to key names
    public static final int[] PREFERENCES_SENSORS_KEYS = {
            R.string.preferenceKey_sensor_accelerometer,
            R.string.preferenceKey_sensor_magneticField,
            R.string.preferenceKey_sensor_orientation,         // virtual
            R.string.preferenceKey_sensor_gyroscope,
            R.string.preferenceKey_sensor_light,
            R.string.preferenceKey_sensor_pressure,
            R.string.preferenceKey_sensor_temperature,
            R.string.preferenceKey_sensor_proximity,
            R.string.preferenceKey_sensor_gravity,             // virtual
            R.string.preferenceKey_sensor_linearAcceleration,  // virtual
            R.string.preferenceKey_sensor_rotationVector,      // virtual
            R.string.preferenceKey_sensor_relativeHumidity,
            R.string.preferenceKey_sensor_ambientTemperature
    };

    // used to map sensor IDs to default values
    public static final int[] PREFERENCES_SENSORS_DEFAULTVALUES = {
            R.string.preferenceValue_sensor_accelerometer,
            R.string.preferenceValue_sensor_magneticField,
            R.string.preferenceValue_sensor_orientation,         // virtual
            R.string.preferenceValue_sensor_gyroscope,
            R.string.preferenceValue_sensor_light,
            R.string.preferenceValue_sensor_pressure,
            R.string.preferenceValue_sensor_temperature,
            R.string.preferenceValue_sensor_proximity,
            R.string.preferenceValue_sensor_gravity,             // virtual
            R.string.preferenceValue_sensor_linearAcceleration,  // virtual
            R.string.preferenceValue_sensor_rotationVector,      // virtual
            R.string.preferenceValue_sensor_relativeHumidity,
            R.string.preferenceValue_sensor_ambientTemperature
    };

    // multiplier for minimum sensor updates
    public static final double[] SENSOR_MINIMUM_UPDATE_SCALES = {
            1.0, // accelerometer
            1.0, // magnetic field
            1.0, // orientation
            1.0, // gyroscope
            1.0, // light
            1.0, // pressure
            1.0, // temperature
            1.0, // proximity
            1.0, // gravity
            1.0, // linear acceleration
            1.0, // rotation vector
            1.0, // relative humidity
            1.0  // ambient temperature
    };
}
