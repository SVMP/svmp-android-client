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

import android.hardware.SensorManager;
import android.util.Log;
import android.view.OrientationEventListener;
import org.mitre.svmp.AppRTCDemoActivity;
import org.mitre.svmp.Utility;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

/**
 * @author Joe Portner
 * When a new screen rotation is detected, this listener sends a message to the VM to update its rotation accordingly
 */
public class RotationHandler extends OrientationEventListener {
    private static final String TAG = RotationHandler.class.getName();

    /**
     * Used to convert degree-based orientation to a screen rotation in 15-degree increments:
     * 0, 15, 30, 45, 60, 75, 90, 105, 120, 135, 150, 165, 180, 195, 210, 225, 240, 255, 270, 285, 300, 315, 330, 345
     * Valid screen rotations are:
     * 0: Surface.ROTATION_0
     * 1: Surface.ROTATION_90
     * 2: Surface.ROTATION_180
     * 3: Surface.ROTATION_270
     */
    private static final int LOOKUP[] = {0, 0, 0, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 0, 0, 0};

    private AppRTCDemoActivity activity;
    private int rotation = -1; // valid values are: 0, 1, 2, 3

    public RotationHandler(AppRTCDemoActivity activity) {
        super(activity.getApplicationContext(), SensorManager.SENSOR_DELAY_UI);
        this.activity = activity;
    }

    public void initRotationUpdates() {
        if (canDetectOrientation()) {
            // get current rotation
            rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            // enable listener for rotation changes
            enable();
            Log.d(TAG, "Can detect orientation, RotationHandler has been enabled");

            // send initial rotation
            sendRotationInfo();
        }
        else
            Log.d(TAG, "Can NOT detect orientation, RotationHandler has NOT been enabled");
    }

    public void cleanupRotationUpdates() {
        disable();
    }

    @Override
    public void onOrientationChanged(int i) {
        if (i != ORIENTATION_UNKNOWN) {
            int iNewOrientation = LOOKUP[i / 15];
            if (rotation != iNewOrientation) {
                rotation = iNewOrientation;
                sendRotationInfo();
            }
        }
    }

    private void sendRotationInfo() {
        if (activity.isConnected()) {
            // construct a Request object
            Request request = Utility.toRequest_RotationInfo(rotation);

            // send the Request to the VM
            activity.sendMessage(request);
        }
    }
}
