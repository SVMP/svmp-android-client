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
import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.protocol.SVMPProtocol.Ping;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

import java.util.TimerTask;

/**
 * @author Joe Portner
 * Runs on an interval to send a ping message to the event server
 * Controlled by PerformanceTimer
 */
public class PingTask extends TimerTask {
    private static final String TAG = PingTask.class.getName();

    private AppRTCActivity activity;

    public PingTask(AppRTCActivity activity) {
        this.activity = activity;
    }

    public void run() {
        Ping.Builder pBuilder = Ping.newBuilder();
        pBuilder.setStartDate(System.currentTimeMillis());

        Request.Builder rBuilder = Request.newBuilder();
        rBuilder.setType(Request.RequestType.PING);
        rBuilder.setPingRequest(pBuilder);
        Request request = rBuilder.build();

        if (activity.isConnected())
        try {
            activity.sendMessage(request);
        } catch (Exception e) {
            Log.d(TAG, "Exception when sending message: " + e.getMessage());
        }
    }
}
