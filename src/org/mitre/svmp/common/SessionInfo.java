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
package org.mitre.svmp.common;

import org.json.JSONObject;
import org.mitre.svmp.apprtc.AppRTCHelper;
import org.mitre.svmp.apprtc.AppRTCSignalingParameters;

/**
 * Contains session-related information obtained from the SVMP Overseer
 * This data is stored in the database so the client can reconnect directly to the Proxy
 * @author Joe Portner
 */
public class SessionInfo {
    private String token;
    private long expires;
    private String host;
    private String port;
    private JSONObject webrtc;
    private AppRTCSignalingParameters signalingParams;

    public SessionInfo(String token, long expires, String host, String port, JSONObject webrtc) {
        this.token = token;
        this.expires = expires;
        this.host = host;
        this.port = port;
        this.webrtc = webrtc;
        this.signalingParams = AppRTCHelper.getParametersForRoom(webrtc);
    }

    public String getToken() {
        return token;
    }

    public long getExpires() {
        return expires;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public JSONObject getWebrtc() {
        return webrtc;
    }

    public AppRTCSignalingParameters getSignalingParams() {
        return signalingParams;
    }
}
