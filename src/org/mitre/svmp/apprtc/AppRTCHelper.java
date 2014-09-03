/*
 * Copyright (c) 2014 The MITRE Corporation, All Rights Reserved.
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

// Derived from GAEChannelClient from the libjingle / webrtc AppRTCDemo
// example application distributed under the following license.
/*
 * libjingle
 * Copyright 2013, Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.mitre.svmp.apprtc;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.VideoStreamInfo;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection.IceServer;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Joe Portner
 * Contains a number of helper functions used throughout AppRTC code
 */
public class AppRTCHelper {
    private static final String TAG = AppRTCHelper.class.getName();

    public static Request makeWebRTCRequest(JSONObject json) {
        WebRTCMessage.Builder rtcmsg = WebRTCMessage.newBuilder();
        rtcmsg.setJson(json.toString());
        try {
            Log.d(TAG, "Sending WebRTC message: " + json.toString(4));
        } catch (JSONException e) {
            // whatever
        }

        return Request.newBuilder()
                .setType(Request.RequestType.WEBRTC)
                .setWebrtcMsg(rtcmsg)
                .build();
    }

    // Poor-man's assert(): die with |msg| unless |condition| is true.
    public static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to put key/value pair in JSON: " + e.getMessage());
        }
    }

    public static AppRTCSignalingParameters getParametersForRoom(JSONObject jsonObject) {
        AppRTCSignalingParameters value = null;

        try {
            MediaConstraints pcConstraints = constraintsFromJSON(jsonObject.getJSONObject("pc"));
            Log.d(TAG, "pc constraints: " + pcConstraints);

            MediaConstraints videoConstraints = constraintsFromJSON(jsonObject.getJSONObject("video"));
            Log.d(TAG, "video constraints: " + videoConstraints);

            LinkedList<IceServer> iceServers = iceServersFromPCConfigJSON(jsonObject.getJSONArray("ice_servers"));
            value = new AppRTCSignalingParameters(iceServers, true, pcConstraints, videoConstraints);
        } catch (JSONException e) {
            Log.e(TAG, "getParametersForRoom failed:", e);
        }
        return value;
    }

    // Mangle SDP to prefer ISAC/16000 over any other audio codec.
    public static String preferISAC(String sdpDescription) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String isac16kRtpMap = null;
        Pattern isac16kPattern =
                Pattern.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
        for (int i = 0;
             (i < lines.length) && (mLineIndex == -1 || isac16kRtpMap == null);
             ++i) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i;
                continue;
            }
            Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1);
                continue;
            }
        }
        if (mLineIndex == -1) {
            Log.d(TAG, "No m=audio line, so can't prefer iSAC");
            return sdpDescription;
        }
        if (isac16kRtpMap == null) {
            Log.d(TAG, "No ISAC/16000 line, so can't prefer iSAC");
            return sdpDescription;
        }
        String[] origMLineParts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int origPartIndex = 0;
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(isac16kRtpMap);
        for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
            if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
                newMLine.append(" ").append(origMLineParts[origPartIndex]);
            }
        }
        lines[mLineIndex] = newMLine.toString();
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    private static MediaConstraints constraintsFromJSON(JSONObject jsonObject) {
        MediaConstraints constraints = new MediaConstraints();
        try {
            JSONObject mandatoryJSON = jsonObject.optJSONObject("mandatory");
            if (mandatoryJSON != null) {
                JSONArray mandatoryKeys = mandatoryJSON.names();
                if (mandatoryKeys != null) {
                    for (int i = 0; i < mandatoryKeys.length(); ++i) {
                        String key = mandatoryKeys.getString(i);
                        String value = mandatoryJSON.getString(key);
                        constraints.mandatory.add(
                                new MediaConstraints.KeyValuePair(key, value));
                    }
                }
            }
            JSONArray optionalJSON = jsonObject.optJSONArray("optional");
            if (optionalJSON != null) {
                for (int i = 0; i < optionalJSON.length(); ++i) {
                    JSONObject keyValueDict = optionalJSON.getJSONObject(i);
                    String key = keyValueDict.names().getString(0);
                    String value = keyValueDict.getString(key);
                    constraints.optional.add(
                            new MediaConstraints.KeyValuePair(key, value));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse MediaConstraints from JSON: " + e.getMessage());
        }
        return constraints;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private static LinkedList<IceServer> iceServersFromPCConfigJSON(JSONArray jsonArray) {
        LinkedList<IceServer> ret = new LinkedList<IceServer>();
        try {
            Log.d(TAG, "ICE server JSON: " + jsonArray.toString(4));
            for (int i = 0; i < jsonArray.length(); ++i) {
                JSONObject server = jsonArray.getJSONObject(i);
                String url = server.getString("url");
                String username =
                        server.has("username") ? server.getString("username") : "";
                String credential =
                        server.has("credential") ? server.getString("credential") : "";
                credential = 
                        server.has("password") ? server.getString("password") : credential;
                ret.add(new IceServer(url, username, credential));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse ICE Servers from PC Config JSON: " + e.getMessage());
        }
        return ret;
    }
}
