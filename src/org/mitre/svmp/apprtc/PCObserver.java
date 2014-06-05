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

import android.graphics.Color;
import android.util.Log;
import org.appspot.apprtc.VideoStreamsView;
import org.json.JSONObject;
import org.mitre.svmp.activities.AppRTCVideoActivity;
import org.mitre.svmp.client.R;
import org.webrtc.*;

import java.util.LinkedList;
import java.util.List;

//Implementation detail: observe ICE & stream changes and react accordingly.
public class PCObserver implements PeerConnection.Observer {
    static final String TAG = PCObserver.class.getName();
    AppRTCVideoActivity activity;
    PeerConnection pc;
    LinkedList<IceCandidate> queuedRemoteCandidates;
    PeerConnectionFactory factory;
    boolean quit;

    public PCObserver(AppRTCVideoActivity activity) {
        this.activity = activity;
        queuedRemoteCandidates = new LinkedList<IceCandidate>();
        quit = false;
    }

    public PeerConnection getPC() {
        return pc;
    }

    public void addIceCandidate(IceCandidate candidate) {
        if (queuedRemoteCandidates != null)
            queuedRemoteCandidates.add(candidate);
        else
            pc.addIceCandidate(candidate);
    }

    // Just for fun (and to regression-test bug 2302) make sure that DataChannels
    // can be created, queried, and disposed.
    public static void createDataChannelToRegressionTestBug2302(PeerConnection pc) {
        DataChannel dc = pc.createDataChannel("dcLabel", new DataChannel.Init());
        AppRTCHelper.abortUnless("dcLabel".equals(dc.label()), "Unexpected label corruption?");
        dc.close();
        dc.dispose();
    }

    public void onIceServers(List<PeerConnection.IceServer> iceServers) {
        factory = new PeerConnectionFactory();
        MediaConstraints pcConstraints = activity.getPCConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));
        pc = factory.createPeerConnection(
                iceServers, pcConstraints, this);

        createDataChannelToRegressionTestBug2302(pc);

        final PeerConnection finalPC = pc;
        final Runnable repeatedStatsLogger = new Runnable() {
            public void run() {
                if (quit)
                    return;

                final Runnable runnableThis = this;
                boolean success = finalPC.getStats(new StatsObserver() {
                    public void onComplete(StatsReport[] reports) {
                        for (StatsReport report : reports)
                            Log.d(TAG, "Stats: " + report.toString());
                        activity.getVSV().postDelayed(runnableThis, 10000);
                    }
                }, null);
                if (!success) {
                    throw new RuntimeException("getStats() return false!");
                }
            }
        };
        activity.getVSV().postDelayed(repeatedStatsLogger, 10000);

        activity.logAndToast(R.string.appRTC_toast_getIceServers_start);
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        if (!quit) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    JSONObject json = new JSONObject();
                    AppRTCHelper.jsonPut(json, "type", "candidate");
                    AppRTCHelper.jsonPut(json, "label", candidate.sdpMLineIndex);
                    AppRTCHelper.jsonPut(json, "id", candidate.sdpMid);
                    AppRTCHelper.jsonPut(json, "candidate", candidate.sdp);

                    activity.sendMessage(AppRTCHelper.makeWebRTCRequest(json));
                }
            });
        }
    }

    public void drainRemoteCandidates() {
        if (queuedRemoteCandidates != null)
            for (IceCandidate candidate : queuedRemoteCandidates)
                pc.addIceCandidate(candidate);
        queuedRemoteCandidates = null;
    }

    @Override
    public void onError() {
        new Thread(new Runnable() {
            public void run() {
                throw new RuntimeException("PeerConnection error!");
            }
        }).start();
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    }

    @Override
    public void onAddStream(final MediaStream stream) {
        new Thread(new Runnable() {
            public void run() {
                AppRTCHelper.abortUnless(//stream.audioTracks.size() == 1 &&
                        stream.videoTracks.size() <= 1,
                        "Weird-looking stream: " + stream);
                if(stream.videoTracks.size() == 1) {
                    stream.videoTracks.get(0).addRenderer(new VideoRenderer(
                            new VideoCallbacks(activity.getVSV(), VideoStreamsView.Endpoint.REMOTE)));
                }

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        activity.stopProgressDialog(); // stop the Progress Dialog
                        activity.getVSV().setBackgroundColor(Color.TRANSPARENT); // video should be started now, remove the background color
                    }});
            }
        }).start();
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
        new Thread(new Runnable() {
            public void run() {
                stream.videoTracks.get(0).dispose();
            }
        }).start();
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
        new Thread(new Runnable() {
            public void run() {
                throw new RuntimeException(
                        "AppRTC doesn't use data channels, but got: " + dc.label() +
                                " anyway!");
            }
        }).start();
    }

    @Override public void onRenegotiationNeeded() {
        // No need to do anything; AppRTC follows a pre-agreed-upon
        // signaling/negotiation protocol.
    }

    public void quit() {
        quit = true;
        if (pc != null) {
            pc.dispose();
            pc = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
    }
}
