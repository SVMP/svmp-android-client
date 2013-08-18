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

// Derived from AppRTCDemoActivity from the libjingle / webrtc AppRTCDemo
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

package org.mitre.svmp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

import org.appspot.apprtc.VideoStreamsView;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.client.LocationHandler;
import org.mitre.svmp.client.NetIntentsHandler;
import org.mitre.svmp.client.SensorHandler;
import org.mitre.svmp.client.TouchHandler;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage.WebRTCType;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRenderer.I420Frame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Main Activity of the SVMP Android client application.
 */
public class AppRTCDemoActivity extends Activity
    implements SVMPAppRTCClient.IceServersObserver, SensorEventListener, LocationListener {
    
  private static final String TAG = "AppRTCDemoActivity";
  
  private PeerConnection pc;
  private final PCObserver pcObserver = new PCObserver();
  private final SDPObserver sdpObserver = new SDPObserver();
  private MediaConstraints sdpMediaConstraints;
  
  private final SVMPChannelClient.MessageHandler clientHandler = new ClientHandler();
  private SVMPAppRTCClient appRtcClient = new SVMPAppRTCClient(this, clientHandler, this);
  
  private VideoStreamsView vsv;
  private Toast logToast;
  private LinkedList<IceCandidate> queuedRemoteCandidates =
      new LinkedList<IceCandidate>();
  // Synchronize on quit[0] to avoid teardown-related crashes.
  private final Boolean[] quit = new Boolean[] { false };
  private PowerManager.WakeLock wakeLock;

  private String host;
  private int port;
  private int encryptionType;
  
  private TouchHandler touchHandler;
  private SensorHandler sensorHandler;
  private LocationHandler locationHandler;
  private boolean connected = false;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Since the error-handling of this demo consists of throwing
    // RuntimeExceptions and we assume that'll terminate the app, we install
    // this default handler so it's applied to background threads as well.
    Thread.setDefaultUncaughtExceptionHandler(
        new Thread.UncaughtExceptionHandler() {
          public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
            System.exit(-1);
          }
        });

    // Uncomment to get ALL WebRTC tracing and SENSITIVE libjingle logging.
    // Logging.enableTracing(
    //     "/sdcard/trace.txt",
    //     EnumSet.of(Logging.TraceLevel.TRACE_ALL),
    //     Logging.Severity.LS_SENSITIVE);

    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "AppRTCDemo");
    wakeLock.acquire();

    Point displaySize = new Point();
    getWindowManager().getDefaultDisplay().getSize(displaySize);
    vsv = new VideoStreamsView(this, displaySize);
    setContentView(vsv);
    
    touchHandler = new TouchHandler(this, displaySize);
    sensorHandler = new SensorHandler(this);
//    locationHandler = new LocationHandler(this);
    
    abortUnless(PeerConnectionFactory.initializeAndroidGlobals(this),
        "Failed to initializeAndroidGlobals");

    AudioManager audioManager =
        ((AudioManager) getSystemService(AUDIO_SERVICE));
    audioManager.setMode(audioManager.isWiredHeadsetOn() ?
        AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
    audioManager.setSpeakerphoneOn(!audioManager.isWiredHeadsetOn());

    sdpMediaConstraints = new MediaConstraints();
    sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
        "OfferToReceiveAudio", "true"));
    sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
        "OfferToReceiveVideo", "true"));

    // Get info passed to Intent
    final Intent intent = getIntent();
    host = intent.getExtras().getString("host");
    port = intent.getExtras().getInt("port");
    encryptionType = intent.getExtras().getInt("encryptionType");

    connectToRoom();
  }

  private void connectToRoom() {
    logAndToast("Connecting to room...");
    appRtcClient.connectToRoom(host, port, encryptionType);
  }

  @Override
  public void onPause() {
    super.onPause();
    vsv.onPause();
    // TODO(fischman): IWBN to support pause/resume, but the WebRTC codebase
    // isn't ready for that yet; e.g.
    // https://code.google.com/p/webrtc/issues/detail?id=1407
    // Instead, simply exit instead of pausing (the alternative leads to
    // system-borking with wedged cameras; e.g. b/8224551)
    disconnectAndExit();
  }

  @Override
  public void onResume() {
    // The onResume() is a lie!  See TODO(fischman) in onPause() above.
    super.onResume();
    vsv.onResume();
  }

  @Override
  public void onIceServers(List<PeerConnection.IceServer> iceServers) {
    PeerConnectionFactory factory = new PeerConnectionFactory();

    pc = factory.createPeerConnection(
        iceServers, appRtcClient.pcConstraints(), pcObserver);

    {
      final PeerConnection finalPC = pc;
      final Runnable repeatedStatsLogger = new Runnable() {
          public void run() {
            synchronized (quit[0]) {
              if (quit[0]) {
                return;
              }
              final Runnable runnableThis = this;
              boolean success = finalPC.getStats(new StatsObserver() {
                  public void onComplete(StatsReport[] reports) {
                    for (StatsReport report : reports) {
                      Log.d(TAG, "Stats: " + report.toString());
                    }
                    vsv.postDelayed(runnableThis, 10000);
                  }
                }, null);
              if (!success) {
                throw new RuntimeException("getStats() return false!");
              }
            }
          }
        };
      vsv.postDelayed(repeatedStatsLogger, 10000);
    }

    {
      logAndToast("Creating local video source...");
      VideoCapturer capturer = getVideoCapturer();
      VideoSource videoSource = factory.createVideoSource(
          capturer, appRtcClient.videoConstraints());
      MediaStream lMS = factory.createLocalMediaStream("ARDAMS");
      VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
      videoTrack.addRenderer(new VideoRenderer(new VideoCallbacks(
          vsv, VideoStreamsView.Endpoint.LOCAL)));
      lMS.addTrack(videoTrack);
      lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));
      pc.addStream(lMS, new MediaConstraints());
    }
    logAndToast("Waiting for ICE candidates...");
  }

  // Cycle through likely device names for the camera and return the first
  // capturer that works, or crash if none do.
  private VideoCapturer getVideoCapturer() {
    String[] cameraFacing = { "front", "back" };
    int[] cameraIndex = { 0, 1 };
    int[] cameraOrientation = { 0, 90, 180, 270 };
    for (String facing : cameraFacing) {
      for (int index : cameraIndex) {
        for (int orientation : cameraOrientation) {
          String name = "Camera " + index + ", Facing " + facing +
              ", Orientation " + orientation;
          VideoCapturer capturer = VideoCapturer.create(name);
          if (capturer != null) {
            logAndToast("Using camera: " + name);
            return capturer;
          }
        }
      }
    }
    throw new RuntimeException("Failed to open capturer");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  // Poor-man's assert(): die with |msg| unless |condition| is true.
  private static void abortUnless(boolean condition, String msg) {
    if (!condition) {
      throw new RuntimeException(msg);
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  // Send |json| to the underlying AppEngine Channel.
  private void sendMessage(JSONObject json) {
    appRtcClient.sendMessage(json.toString());
  }
  
  public void sendMessage(Request msg) {
      appRtcClient.sendMessage(msg);
  }

  // Put a |key|->|value| mapping in |json|.
  private static void jsonPut(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  // Implementation detail: observe ICE & stream changes and react accordingly.
  private class PCObserver implements PeerConnection.Observer {
    @Override public void onIceCandidate(final IceCandidate candidate){
      runOnUiThread(new Runnable() {
          public void run() {
            JSONObject json = new JSONObject();
            // peerconnection_client is brain dead... 
            // it uses the presence or lack of the "type" field to differentiate sdp from candidate
            // type present -> sdp, type absent -> candidate
//            jsonPut(json, "type", "candidate");
//          jsonPut(json, "label", candidate.sdpMLineIndex);
//          jsonPut(json, "id", candidate.sdpMid);
            jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
            jsonPut(json, "sdpMid", candidate.sdpMid);
            jsonPut(json, "candidate", candidate.sdp);
            sendMessage(json);
          }
        });
    }

    @Override public void onError(){
      runOnUiThread(new Runnable() {
          public void run() {
            throw new RuntimeException("PeerConnection error!");
          }
        });
    }

    @Override public void onSignalingChange(
        PeerConnection.SignalingState newState) {
    }

    @Override public void onIceConnectionChange(
        PeerConnection.IceConnectionState newState) {
    }

    @Override public void onIceGatheringChange(
        PeerConnection.IceGatheringState newState) {
    }

    @Override public void onAddStream(final MediaStream stream){
      runOnUiThread(new Runnable() {
          public void run() {
            abortUnless(//stream.audioTracks.size() == 1 &&
                stream.videoTracks.size() == 1,
                "Weird-looking stream: " + stream);
            stream.videoTracks.get(0).addRenderer(new VideoRenderer(
                new VideoCallbacks(vsv, VideoStreamsView.Endpoint.REMOTE)));
          }
        });
    }

    @Override public void onRemoveStream(final MediaStream stream){
      runOnUiThread(new Runnable() {
          public void run() {
            stream.videoTracks.get(0).dispose();
          }
        });
    }

    @Override public void onDataChannel(final DataChannel dc) {
      runOnUiThread(new Runnable() {
          public void run() {
            throw new RuntimeException(
                "AppRTC doesn't use data channels, but got: " + dc.label() +
                " anyway!");
          }
        });
    }
  }

  // Implementation detail: handle offer creation/signaling and answer setting,
  // as well as adding remote ICE candidates once the answer SDP is set.
  private class SDPObserver implements SdpObserver {
    @Override public void onCreateSuccess(final SessionDescription sdp) {
      runOnUiThread(new Runnable() {
          public void run() {
            logAndToast("Sending " + sdp.type);
            JSONObject json = new JSONObject();
            jsonPut(json, "type", sdp.type.canonicalForm());
            jsonPut(json, "sdp", sdp.description);
            sendMessage(json);
            pc.setLocalDescription(sdpObserver, sdp);
          }
        });
    }

    @Override public void onSetSuccess() {
      runOnUiThread(new Runnable() {
          public void run() {
            if (appRtcClient.isInitiator()) {
              if (pc.getRemoteDescription() != null) {
                // We've set our local offer and received & set the remote
                // answer, so drain candidates.
                drainRemoteCandidates();
              }
            } else {
              if (pc.getLocalDescription() == null) {
                // We just set the remote offer, time to create our answer.
                logAndToast("Creating answer");
                pc.createAnswer(SDPObserver.this, sdpMediaConstraints);
              } else {
                // Sent our answer and set it as local description; drain
                // candidates.
                drainRemoteCandidates();
              }
            }
          }
        });
    }

    @Override public void onCreateFailure(final String error) {
      runOnUiThread(new Runnable() {
          public void run() {
            throw new RuntimeException("createSDP error: " + error);
          }
        });
    }

    @Override public void onSetFailure(final String error) {
      runOnUiThread(new Runnable() {
          public void run() {
            throw new RuntimeException("setSDP error: " + error);
          }
        });
    }

    private void drainRemoteCandidates() {
      for (IceCandidate candidate : queuedRemoteCandidates) {
        pc.addIceCandidate(candidate);
      }
      queuedRemoteCandidates = null;
    }
  }

  // Implementation detail: handler for receiving SVMP protocol messages and
  // dispatching them appropriately.
  private class ClientHandler implements SVMPChannelClient.MessageHandler {
    public void onOpen() {
      if (!appRtcClient.isInitiator()) {
        return;
      }
      connected = true;
      touchHandler.sendScreenInfoMessage();
      sensorHandler.initSensors();
      
      logAndToast("Creating offer...");
      pc.createOffer(sdpObserver, sdpMediaConstraints);
    }

    public void onMessage(Response data) {
      switch (data.getType()) {
      case SCREENINFO:
          handleScreenInfo(data);
      case LOCATION:
          handleLocationResponse(data);
          break;
          // This is an ACK to the video STOP request.
      case INTENT:
      case NOTIFICATION:
          //Inspect this message to see if it's an intent or notification.
          NetIntentsHandler.inspect(data, AppRTCDemoActivity.this);
          break;
      case WEBRTC:
        try {
          JSONObject json = new JSONObject(data.getWebrtcMsg().getJson());
          String type;
          // hacky workaround for the fact that peerconnection_client doesn't put a "type" on candidates
          try {
            type = (String) json.get("type");
          } catch (JSONException e) {
            json.put("type", "candidate");
            type = (String) json.get("type");
          }
          if (type.equals("candidate")) {
            IceCandidate candidate = new IceCandidate(
//                (String) json.get("id"),
//                json.getInt("label"),
                (String) json.get("sdpMid"),
                json.getInt("sdpMLineIndex"),
                (String) json.get("candidate"));
            if (queuedRemoteCandidates != null) {
              queuedRemoteCandidates.add(candidate);
            } else {
              pc.addIceCandidate(candidate);
            }
          } else if (type.equals("answer") || type.equals("offer")) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                (String) json.get("sdp"));
            pc.setRemoteDescription(sdpObserver, sdp);
          } else if (type.equals("bye")) {
            logAndToast("Remote end hung up; dropping PeerConnection");
            disconnectAndExit();
          } else {
            throw new RuntimeException("Unexpected message: " + data);
          }
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
        break;
      default:
        Log.e(TAG, "Unexpected protocol message of type " + data.getType().name());
      }

    }

    public void onClose() {
      disconnectAndExit();
    }

    public void onError(int code, String description) {
      disconnectAndExit();
    }
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnectAndExit() {
    synchronized (quit[0]) {
      if (quit[0]) {
        return;
      }
      quit[0] = true;
      wakeLock.release();
      if (pc != null) {
        pc.dispose();
        pc = null;
      }
      if (appRtcClient != null) {
//        appRtcClient.sendMessage("{\"type\": \"bye\"}");
        Request bye = Request.newBuilder().setType(RequestType.WEBRTC)
                .setWebrtcMsg(WebRTCMessage.newBuilder().setType(WebRTCType.BYE)).build();
        appRtcClient.sendMessage(bye);
        try {
          appRtcClient.disconnect();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        appRtcClient = null;
      }
      finish();
    }
  }

  // Implementation detail: bridge the VideoRenderer.Callbacks interface to the
  // VideoStreamsView implementation.
  private class VideoCallbacks implements VideoRenderer.Callbacks {
    private final VideoStreamsView view;
    private final VideoStreamsView.Endpoint stream;

    public VideoCallbacks(
        VideoStreamsView view, VideoStreamsView.Endpoint stream) {
      this.view = view;
      this.stream = stream;
    }

    @Override
    public void setSize(final int width, final int height) {
      view.queueEvent(new Runnable() {
          public void run() {
            view.setSize(stream, width, height);
          }
        });
    }

    @Override
    public void renderFrame(I420Frame frame) {
      view.queueFrame(stream, frame);
    }
  }

  public boolean isConnected() {
      return connected;
  }
  
  /////////////////////////////////////////////////////////////////////
  // Bridge the SensorEventListener callbacks to the SensorHandler
  /////////////////////////////////////////////////////////////////////
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    if (connected)
      sensorHandler.onAccuracyChanged(sensor, accuracy);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (connected)
      sensorHandler.onSensorChanged(event);
  }

  public SensorManager getSensorManager() {
    return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
  }


  /////////////////////////////////////////////////////////////////////
  // Bridge input callbacks to the Touch Input Handler
  /////////////////////////////////////////////////////////////////////

  private void handleScreenInfo(Response msg) {
    touchHandler.handleScreenInfoResponse(msg);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (connected)
      return touchHandler.onTouchEvent(event);
    else
      return false;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
      return super.onKeyDown(keyCode, event);
//    if (connected)
//      return touchHandler.onKeyDown(keyCode, event);
//    else
//      return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    return super.onKeyUp(keyCode, event);
//    if (connected)
//      return touchHandler.onKeyUp(keyCode, event);
//    else
//      return false;
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    return super.onTrackballEvent(event);
//    if (connected)
//      return touchHandler.onTrackballEvent(event);
//    else
//      return false;
  }
  
  /////////////////////////////////////////////////////////////////////
  // Bridge LocationListener callbacks to the Location Handler
  /////////////////////////////////////////////////////////////////////
  private void handleLocationResponse(Response msg) {
//      locationHandler.handleLocationResponse(msg);
  }
  
  public LocationManager getLocationManager() {
    return (LocationManager) getSystemService(Context.LOCATION_SERVICE);
  }
  
  @Override
  public void onLocationChanged(Location location) {
    if (connected)
      locationHandler.onLocationChanged(location);
  }

  @Override
  public void onProviderDisabled(String provider) {
    if (connected)
      locationHandler.onProviderDisabled(provider);
  }
    
  @Override
  public void onProviderEnabled(String provider) {
    if (connected)
      locationHandler.onProviderEnabled(provider);
  }
    
  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    if (connected)
      locationHandler.onStatusChanged(provider, status, extras);
  }
}
