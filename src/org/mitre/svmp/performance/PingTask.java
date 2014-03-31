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

import org.mitre.svmp.apprtc.AppRTCClient;
import org.mitre.svmp.protocol.SVMPProtocol.Ping;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

import java.util.TimerTask;

/**
 * @author Joe Portner
 * Runs on an interval to send a ping message to the event server
 * Controlled by PerformanceTimer
 */
public class PingTask extends TimerTask {
    private AppRTCClient binder;

    public PingTask(AppRTCClient binder) {
        this.binder = binder;
    }

    public void run() {
        binder.sendMessage(makePingRequest());
    }

    private Request makePingRequest() {
        Ping.Builder pBuilder = Ping.newBuilder();
        pBuilder.setStartDate(System.currentTimeMillis());

        return Request.newBuilder()
                .setType(Request.RequestType.PING)
                .setPingRequest(pBuilder)
                .build();
    }
}
