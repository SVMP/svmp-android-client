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
package org.mitre.svmp.apprtc;

import android.util.Log;
import org.json.JSONObject;
import org.mitre.svmp.activities.AppRTCVideoActivity;
import org.mitre.svmp.client.R;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

// Implementation detail: handle offer creation/signaling and answer setting,
// as well as adding remote ICE candidates once the answer SDP is set.
public class SDPObserver implements SdpObserver {
    protected static final String TAG = SDPObserver.class.getName();
    AppRTCVideoActivity activity;

    public SDPObserver(AppRTCVideoActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
        final SDPObserver parent = this;
        new Thread(new Runnable() {
            public void run() {
                activity.logAndToast(R.string.appRTC_toast_sdpObserver_sendOffer);
                SessionDescription sdp = new SessionDescription(
                        origSdp.type, AppRTCHelper.preferISAC(origSdp.description));

                activity.getPCObserver().getPC().setLocalDescription(parent, sdp);
            }
        }).start();
    }

    // Helper for sending local SDP (offer or answer, depending on role) to the
    // other participant.
    private void sendLocalDescription(PeerConnection pc) {
        SessionDescription sdp = pc.getLocalDescription();
        //logAndToast("Sending " + sdp.type);
        JSONObject json = new JSONObject();
        AppRTCHelper.jsonPut(json, "type", sdp.type.canonicalForm());
        AppRTCHelper.jsonPut(json, "sdp", sdp.description);
        activity.sendMessage(AppRTCHelper.makeWebRTCRequest(json));
    }

    @Override public void onSetSuccess() {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                PCObserver pcObserver = activity.getPCObserver();
                //if (activity.isInitiator()) {
                    if (pcObserver.getPC().getRemoteDescription() != null) {
                        // We've set our local offer and received & set the remote
                        // answer, so drain candidates.
                        pcObserver.drainRemoteCandidates();
                    } else {
                        // We've just set our local description so time to send it.
                        sendLocalDescription(pcObserver.getPC());
                    }
                //}
/* we are always the initiator, no need for this condition
                else {
                    if (pcObserver.getPC().getLocalDescription() == null) {
                        // We just set the remote offer, time to create our answer.
                        //logAndToast("Creating answer");
                        pcObserver.getPC().createAnswer(SDPObserver.this, activity.getSdpMediaConstraints());
                    } else {
                        // Answer now set as local description; send it and drain
                        // candidates.
                        sendLocalDescription(pcObserver.getPC());
                        pcObserver.drainRemoteCandidates();
                    }
                }
*/
            }
        });
    }

    @Override
    public void onCreateFailure(final String error) {
        new Thread(new Runnable() {
            public void run() {
                Log.e(TAG, "createSDP failed: " + error);
                activity.changeToErrorState();
            }
        }).start();
    }

    @Override
    public void onSetFailure(final String error) {
        new Thread(new Runnable() {
            public void run() {
                Log.e(TAG, "setSDP failed: " + error);
                activity.changeToErrorState();
            }
        }).start();
    }
}
