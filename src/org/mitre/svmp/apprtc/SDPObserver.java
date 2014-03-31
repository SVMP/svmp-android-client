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

import org.json.JSONObject;
import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.client.R;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

// Implementation detail: handle offer creation/signaling and answer setting,
// as well as adding remote ICE candidates once the answer SDP is set.
public class SDPObserver implements SdpObserver {
    protected static final String TAG = SDPObserver.class.getName();
    AppRTCActivity activity;

    public SDPObserver(AppRTCActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onCreateSuccess(final SessionDescription sdp) {
        final SDPObserver parent = this;
        new Thread(new Runnable() {
            public void run() {
                activity.logAndToast(R.string.appRTC_toast_sdpObserver_sendOffer);
                JSONObject json = new JSONObject();
                AppRTCHelper.jsonPut(json, "type", sdp.type.canonicalForm());
                AppRTCHelper.jsonPut(json, "sdp", sdp.description);

                activity.sendMessage(json);
                activity.getPCObserver().getPC().setLocalDescription(parent, sdp);
            }
        }).start();
    }

    @Override
    public void onSetSuccess() {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (activity.isInitiator()) {
                    if (activity.getPCObserver().getPC().getRemoteDescription() != null) {
                        // We've set our local offer and received & set the remote
                        // answer, so drain candidates.
                        activity.getPCObserver().drainRemoteCandidates();
                    }
                } else {
                    if (activity.getPCObserver().getPC().getLocalDescription() == null) {
                        // We just set the remote offer, time to create our answer.
                        activity.logAndToast(R.string.appRTC_toast_sdpObserver_createAnswer);
                        activity.getPCObserver().getPC().createAnswer(SDPObserver.this, activity.getSdpMediaConstraints());
                    } else {
                        // Sent our answer and set it as local description; drain
                        // candidates.
                        activity.getPCObserver().drainRemoteCandidates();
                    }
                }
            }
        });
    }

    @Override
    public void onCreateFailure(final String error) {
        new Thread(new Runnable() {
            public void run() {
                throw new RuntimeException("createSDP error: " + error);
            }
        }).start();
    }

    @Override
    public void onSetFailure(final String error) {
        new Thread(new Runnable() {
            public void run() {
                throw new RuntimeException("setSDP error: " + error);
            }
        }).start();
    }
}
