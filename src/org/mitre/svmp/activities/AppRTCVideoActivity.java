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

// Derived from AppRTCActivity from the libjingle / webrtc AppRTCDemo
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

package org.mitre.svmp.activities;

import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import org.appspot.apprtc.VideoStreamsView;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.apprtc.*;
import org.mitre.svmp.client.R;
import org.mitre.svmp.client.RotationHandler;
import org.mitre.svmp.client.TouchHandler;
import org.mitre.svmp.protocol.SVMPProtocol.AppsRequest;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.webrtc.*;

/**
 * Main Activity of the SVMP Android client application.
 */
public class AppRTCVideoActivity extends AppRTCActivity {
    private static final String TAG = AppRTCVideoActivity.class.getName();

    private MediaConstraints sdpMediaConstraints;
    private SDPObserver sdpObserver;
    private VideoStreamsView vsv;
    private PCObserver pcObserver;
    private TouchHandler touchHandler;
    private RotationHandler rotationHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void connectToRoom() {
        // Uncomment to get ALL WebRTC tracing and SENSITIVE libjingle logging.
//        Logging.enableTracing(
//            "/sdcard/trace.txt",
//            EnumSet.of(Logging.TraceLevel.TRACE_ALL),
//            Logging.Severity.LS_SENSITIVE);

        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        vsv = new VideoStreamsView(this, displaySize, performanceAdapter);
        vsv.setBackgroundColor(Color.DKGRAY); // start this VideoStreamsView with a color of dark gray
        setContentView(vsv);

        touchHandler = new TouchHandler(this, displaySize, performanceAdapter);
        rotationHandler = new RotationHandler(this);

        AppRTCHelper.abortUnless(PeerConnectionFactory.initializeAndroidGlobals(this),
                "Failed to initializeAndroidGlobals");

        //Create observers.
        sdpObserver = new SDPObserver(this);
        pcObserver = new PCObserver(this);

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));

        super.connectToRoom();
    }

    public VideoStreamsView getVSV() {
        return vsv;
    }

    public PCObserver getPCObserver() {
        return pcObserver;
    }

/*
    public MediaConstraints getSdpMediaConstraints() {
        return sdpMediaConstraints;
    }

    public boolean isInitiator() {
        return appRtcClient.isInitiator();
    }
*/

    // called from PCObserver
    public MediaConstraints getPCConstraints() {
        MediaConstraints value = null;
        if (appRtcClient != null)
            value = appRtcClient.pcConstraints();
        return value;
    }

    @Override
    protected void startProgressDialog() {
        vsv.setBackgroundColor(Color.DKGRAY); // if it isn't already set, make the background color dark gray
        super.startProgressDialog();
    }

    @Override
    public void onPause() {
        vsv.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        vsv.onResume();
        super.onResume();
    }

    // MessageHandler interface method
    // Called when the client connection is established
    @Override
    public void onOpen() {
        super.onOpen();

        // set up ICE servers
        pcObserver.onIceServers(appRtcClient.getSignalingParams().iceServers);

        touchHandler.sendScreenInfoMessage();
        rotationHandler.initRotationUpdates();

        PeerConnection pc = pcObserver.getPC();
        if (pc != null)
            pc.createOffer(sdpObserver, sdpMediaConstraints);

        // if we've been given a package name, start that app
        if (pkgName != null) {
            AppsRequest.Builder aBuilder = AppsRequest.newBuilder();
            aBuilder.setType(AppsRequest.AppsRequestType.LAUNCH);
            aBuilder.setPkgName(pkgName);
            Request.Builder rBuilder = Request.newBuilder();
            rBuilder.setType(Request.RequestType.APPS);
            rBuilder.setApps(aBuilder);
            sendMessage(rBuilder.build());
        }
    }

    // MessageHandler interface method
    // Called when a message is sent from the server, and the SessionService doesn't consume it
    public boolean onMessage(Response data) {
        switch (data.getType()) {
            case SCREENINFO:
                handleScreenInfo(data);
                break;
            case WEBRTC:
                try {
                    JSONObject json = new JSONObject(data.getWebrtcMsg().getJson());
                    Log.d(TAG, "Received WebRTC message from peer:\n" + json.toString(4));
                    String type;
                    // peerconnection_client doesn't put a "type" on candidates
                    try {
                        type = (String) json.get("type");
                    } catch (JSONException e) {
                        json.put("type", "candidate");
                        type = (String) json.get("type");
                    }

                    //Check out the type of WebRTC message.
                    if (type.equals("candidate")) {
                        IceCandidate candidate = new IceCandidate(
                                (String) json.get("id"),
                                json.getInt("label"),
                                (String) json.get("candidate"));
                        getPCObserver().addIceCandidate(candidate);
                    } else if (type.equals("answer") || type.equals("offer")) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                AppRTCHelper.preferISAC((String) json.get("sdp")));
                        getPCObserver().getPC().setRemoteDescription(sdpObserver, sdp);
                    } else if (type.equals("bye")) {
                        logAndToast(R.string.appRTC_toast_clientHandler_finish);
                        disconnectAndExit();
                    } else {
                        throw new RuntimeException("Unexpected message: " + data);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                // any messages we don't understand, pass to our parent for processing
                super.onMessage(data);
        }
        return true;
    }

    @Override
    protected void onDisconnectAndExit() {
        if (rotationHandler != null)
            rotationHandler.cleanupRotationUpdates();
        if (pcObserver != null)
            pcObserver.quit();
    }

    /////////////////////////////////////////////////////////////////////
    // Bridge input callbacks to the Touch Input Handler
    /////////////////////////////////////////////////////////////////////
    private void handleScreenInfo(Response msg) {
        touchHandler.handleScreenInfoResponse(msg);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return proxying && touchHandler.onTouchEvent(event);
    }
}
