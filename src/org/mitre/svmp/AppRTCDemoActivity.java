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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import org.appspot.apprtc.VideoStreamsView;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.client.*;
import org.mitre.svmp.observers.PCObserver;
import org.mitre.svmp.observers.SDPObserver;
import org.mitre.svmp.performance.PerformanceTimer;
import org.mitre.svmp.performance.PointPerformanceData;
import org.mitre.svmp.performance.SpanPerformanceData;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage.WebRTCType;
import org.webrtc.*;
import org.mitre.svmp.StateMachine.STATE;

import java.util.List;

/**
 * Main Activity of the SVMP Android client application.
 */
public class AppRTCDemoActivity extends Activity implements SVMPAppRTCClient.IceServersObserver, StateObserver,
        SensorEventListener, Constants {

    private static final String TAG = AppRTCDemoActivity.class.getName();

    private MediaConstraints sdpMediaConstraints;

    private final SVMPChannelClient.MessageHandler clientHandler = new ClientHandler();
    private SVMPAppRTCClient appRtcClient;
    private SessionService service;
    private boolean bound = false;

    private SDPObserver sdpObserver;
    private VideoStreamsView vsv;
    private PCObserver pcObserver;
    private Toast logToast;

    // Synchronize on quit[0] to avoid teardown-related crashes.
    private final Boolean[] quit = new Boolean[]{false};

    private DatabaseHandler dbHandler;
    private ConnectionInfo connectionInfo;
    private PerformanceTimer performanceTimer;
    private SpanPerformanceData spanPerformanceData;
    private PointPerformanceData pointPerformanceData;

    private TouchHandler touchHandler;
    private SensorHandler sensorHandler;
    private LocationHandler locationHandler;
    private RotationHandler rotationHandler;
    private boolean connected = false; // if this is true, we have established a socket connection
    private boolean proxying = false; // if this is true, we have finished the handshakes and the connection is running
    private ProgressDialog pd;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideNavBar();

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // create an object to hold performance measurements
        spanPerformanceData = new SpanPerformanceData();
        pointPerformanceData = new PointPerformanceData();

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
//        Logging.enableTracing(
//            "/sdcard/trace.txt",
//            EnumSet.of(Logging.TraceLevel.TRACE_ALL),
//            Logging.Severity.LS_SENSITIVE);

        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        vsv = new VideoStreamsView(this, displaySize, spanPerformanceData);
        vsv.setBackgroundColor(Color.DKGRAY); // start this VideoStreamsView with a color of dark gray
        setContentView(vsv);

        touchHandler = new TouchHandler(this, spanPerformanceData, displaySize);
        sensorHandler = new SensorHandler(this, spanPerformanceData);
        locationHandler = new LocationHandler(this);
        rotationHandler = new RotationHandler(this);

        abortUnless(PeerConnectionFactory.initializeAndroidGlobals(this),
                "Failed to initializeAndroidGlobals");

        //Create observers.
        sdpObserver = new SDPObserver(this);
        pcObserver = new PCObserver(this);

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));

        // Get info passed to Intent
        final Intent intent = getIntent();
        connectionInfo = dbHandler.getConnectionInfo(intent.getIntExtra("connectionID", 0));

        if (connectionInfo != null)
            connectToRoom();
        else
            logAndToast(R.string.appRTC_toast_connection_notFound);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (true) {
            hideNavBar();
        }
    }

    @SuppressLint("InlinedApi")
    private void hideNavBar() {
        // hide the nav and notification bars
        View decorView = this.getWindow().getDecorView();
        int uiOptions = 0;
        if (API_KITKAT) {
            // use the new immersive full-screen mode
            // https://developer.android.com/training/system-ui/immersive.html
            uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        else if (API_ICS) {
            // ICS nav bar dimming
            // Notification bar is done in the manifest properties for this activity:
            //    android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"
            uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        }

        decorView.setSystemUiVisibility(uiOptions);
    }

    public VideoStreamsView getVSV() {
        return vsv;
    }

    public PCObserver getPCObserver() {
        return pcObserver;
    }

    public MediaConstraints getSdpMediaConstraints() {
        return sdpMediaConstraints;
    }

    public boolean isInitiator() {
        return appRtcClient.isInitiator();
    }

    public SVMPAppRTCClient getBinder() {
        return appRtcClient;
    }

    private void connectToRoom() {
        logAndToast(R.string.appRTC_toast_connection_start);
        startProgressDialog();

        bindService(new Intent(this, SessionService.class), serviceConnection, 0);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder iBinder) {
            // We've bound to SessionService, cast the IBinder and get SessionService instance
            appRtcClient = (SVMPAppRTCClient) iBinder;
            service = appRtcClient.getService();
            bound = true;

            // after we have bound to the service, begin the connection
            appRtcClient.connectToRoom(AppRTCDemoActivity.this, clientHandler, AppRTCDemoActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    public void startProgressDialog() {
        vsv.setBackgroundColor(Color.DKGRAY); // if it isn't already set, make the background color dark gray
        pd = new ProgressDialog(AppRTCDemoActivity.this);
        pd.setTitle(R.string.appRTC_progressDialog_title);
        pd.setMessage(getResources().getText(R.string.appRTC_progressDialog_message));
        pd.setIndeterminate(true);
        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                disconnectAndExit();
            }
        });
        pd.show();
    }

    public void stopProgressDialog() {
        if (pd != null) {
            pd.dismiss();
            pd = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if (connected)
            disconnectAndExit();
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
    }

    @Override
    public void onIceServers(List<PeerConnection.IceServer> iceServers) {
        pcObserver.onIceServers(iceServers);
    }

    @Override
    public void onDestroy() {
        if (performanceTimer != null)
            performanceTimer.cancel();
        super.onDestroy();
    }

    // Poor-man's assert(): die with |msg| unless |condition| is true.
    private static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    // Log |msg| and Toast about it.
    public void logAndToast(final int resID) {
        Log.d(TAG, getResources().getString(resID));
        this.runOnUiThread(new Runnable() {
            public void run() {
                if (logToast != null) {
                    logToast.cancel();
                }
                logToast = Toast.makeText(AppRTCDemoActivity.this, resID, Toast.LENGTH_SHORT);
                logToast.show();
            }
        });
    }

    // Send |json| to the underlying AppEngine Channel.
    public void sendMessage(JSONObject json) {
        appRtcClient.sendMessage(json.toString());
    }

    public void sendMessage(Request msg) {
        appRtcClient.sendMessage(msg);
    }

    // Implementation detail: handler for receiving SVMP protocol messages and
    // dispatching them appropriately.
    private class ClientHandler implements SVMPChannelClient.MessageHandler {
        public void onOpen() {
            if (!appRtcClient.isInitiator()) {
                return;
            }
            proxying = true;

            // create a timer to start taking measurements
            performanceTimer = new PerformanceTimer(AppRTCDemoActivity.this, spanPerformanceData, pointPerformanceData,
                    connectionInfo.getConnectionID());

            touchHandler.sendScreenInfoMessage();
            sensorHandler.initSensors();
            locationHandler.initLocationUpdates();
            rotationHandler.initRotationUpdates();

            logAndToast(R.string.appRTC_toast_clientHandler_start);
            pcObserver.getPC().createOffer(sdpObserver, sdpMediaConstraints);
        }

        public void onMessage(Response data) {
            switch (data.getType()) {
                case AUTH:
                    if (data.hasAuthResponse()) {
                        switch (data.getAuthResponse().getType()) {
                            case SESSION_MAX_TIMEOUT:
                                needAuth(R.string.svmpActivity_toast_sessionMaxTimeout);
                                break;
                            case SESSION_IDLE_TIMEOUT:
                                needAuth(R.string.svmpActivity_toast_sessionIdleTimeout);
                                break;
                        }
                    }
                    break;
                case SCREENINFO:
                    handleScreenInfo(data);
                    break;
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
                        // peerconnection_client doesn't put a "type" on candidates
                        try {
                            type = (String) json.get("type");
                        } catch (JSONException e) {
                            json.put("type", "candidate");
                            type = (String) json.get("type");
                        }

                        //Check out the type of WebRTC message.
                        if (type.equals("candidate")) {
                            getPCObserver().addIceCandidate(
                                    new IceCandidate((String) json.get("sdpMid"), json.getInt("sdpMLineIndex"), (String) json.get("candidate"))
                            );
                        } else if (type.equals("answer") || type.equals("offer")) {
                            SessionDescription sdp = new SessionDescription(
                                    SessionDescription.Type.fromCanonicalForm(type),
                                    (String) json.get("sdp"));
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
                case PING:
                    long endDate = System.currentTimeMillis(); // immediately get end date
                    if (data.hasPingResponse())
                        pointPerformanceData.setPing(data.getPingResponse().getStartDate(), endDate);
                    break;
                default:
                    Log.e(TAG, "Unexpected protocol message of type " + data.getType().name());
            }

        }

        // when authentication fails, or a session maxTimeout or idleTimeout message is received, stop the
        // AppRTCDemoActivity, close the connection, and cause the ConnectionList activity to reconnect to this
        // connectionID
        public void needAuth(int messageResID) {
            // clear timed out session information from memory
            dbHandler.clearSessionInfo(connectionInfo);
            // send a result message to the calling activity so it will show the authentication dialog again
            Intent intent = new Intent();
            intent.putExtra("connectionID", connectionInfo.getConnectionID());
            if (messageResID > 0)
                intent.putExtra("message", messageResID); // toast to show to user when the activity finishes
            setResult(SvmpActivity.RESULT_NEEDAUTH, intent);
            disconnectAndExit();
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
        proxying = false;
        synchronized (quit[0]) {
            if (quit[0]) {
                return;
            }
            quit[0] = true;

            // Unbind from the service
            if (bound) {
                unbindService(serviceConnection);
                bound = false;
            }

            // TODO: stopping service here, remove this eventually
            Intent intent = new Intent(this, SessionService.class);
            stopService(intent);

            pcObserver.quit();
            stopProgressDialog(); // prevent resource leak if we disconnect while the progress dialog is still up
            if (appRtcClient != null) {
                Request bye = Request.newBuilder().setType(RequestType.WEBRTC)
                        .setWebrtcMsg(WebRTCMessage.newBuilder().setType(WebRTCType.BYE)).build();
                try {
                    appRtcClient.sendMessage(bye);
                } catch (Exception e) {
                    // don't care
                }
                try {
                    appRtcClient.disconnect();
                } catch (Exception e) {
                    // don't care
                }
                appRtcClient = null;
            }
            if (performanceTimer != null)
                performanceTimer.cancel();
            if (!isFinishing())
                finish();
        }
    }

    public boolean isConnected() {
        return proxying;
    }

    public void onStateChange(STATE oldState, STATE newState, int resID) {
        // if the state change included a message, log it and display a toast popup message
        if (resID > 0)
            logAndToast(resID);

        switch(newState) {
            case CONNECTED:
                connected = true;
                break;
            case AUTH:
                break;
            case READY:
                break;
            case RUNNING:
                break;
            case ERROR:
                // by default, when the connection list resumes, don't do anything
                Intent intent = new Intent();
                setResult(SvmpActivity.RESULT_CANCELED, intent);

                // if we are in an error state, check the previous state and act appropriately
                switch(oldState) {
                    case STARTED: // failed to connect the socket and transition to CONNECTED
                        // the socket connection failed, display the failure message and return to the connection list
                        break;
                    case CONNECTED: // failed to authenticate and transition to AUTH
                        if (resID == R.string.appRTC_toast_svmpAuthenticator_fail) {
                            // our authentication was rejected, bring up the auth prompt when the connection list resumes
                            intent.putExtra("connectionID", connectionInfo.getConnectionID());
                            setResult(SvmpActivity.RESULT_NEEDAUTH, intent);
                        }
                        // otherwise, we had an SSL error, display the failure message and return to the connection list
                        break;
                    case AUTH: // failed to receive ready message and transition to READY
                        break;
                    case READY: // failed to receive video parameters and transition to RUNNING
                        break;
                    case RUNNING: // failed after already running
                        break;
                }

                // finish this activity and return to the connection list
                finish();
                break;
            default:
                break;
        }
    }

    /////////////////////////////////////////////////////////////////////
    // Bridge the SensorEventListener callbacks to the SensorHandler
    /////////////////////////////////////////////////////////////////////
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (proxying)
            sensorHandler.onAccuracyChanged(sensor, accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (proxying)
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
        return proxying && touchHandler.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return super.onTrackballEvent(event);
    }

    /////////////////////////////////////////////////////////////////////
    // Bridge LocationListener callbacks to the Location Handler
    /////////////////////////////////////////////////////////////////////
    private void handleLocationResponse(Response msg) {
        locationHandler.handleLocationResponse(msg);
    }
}
