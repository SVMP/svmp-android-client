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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import org.appspot.apprtc.VideoStreamsView;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.apprtc.*;
import org.mitre.svmp.client.*;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.DatabaseHandler;
import org.mitre.svmp.common.StateObserver;
import org.mitre.svmp.performance.PerformanceAdapter;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.services.SessionService;
import org.webrtc.*;
import org.mitre.svmp.common.StateMachine.STATE;

/**
 * Main Activity of the SVMP Android client application.
 */
public class AppRTCActivity extends Activity implements StateObserver, MessageHandler, Constants {

    private static final String TAG = AppRTCActivity.class.getName();

    private MediaConstraints sdpMediaConstraints;

    private AppRTCClient appRtcClient;
    private SessionService service;
    private PerformanceAdapter performanceAdapter;
    private boolean bound = false;

    private SDPObserver sdpObserver;
    private VideoStreamsView vsv;
    private PCObserver pcObserver;
    private Toast logToast;

    // Synchronize on quit[0] to avoid teardown-related crashes.
    private final Boolean[] quit = new Boolean[]{false};

    private DatabaseHandler dbHandler;
    private ConnectionInfo connectionInfo;

    private TouchHandler touchHandler;
    private RotationHandler rotationHandler;
    private boolean proxying = false; // if this is true, we have finished the handshakes and the connection is running
    private ProgressDialog pd;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideNavBar();

        // lock the application to the natural "up" orientation of the physical device
        //noinspection MagicConstant
        setRequestedOrientation(getDeviceDefaultOrientation());

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // adapter that helps record performance measurements
        performanceAdapter = new PerformanceAdapter();

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

    // returns what value we should request for screen orientation, either portrait or landscape
    private int getDeviceDefaultOrientation() {
        WindowManager windowManager =  (WindowManager) getSystemService(WINDOW_SERVICE);
        Configuration config = getResources().getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();

        int value = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if ( ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE)
                || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
                config.orientation == Configuration.ORIENTATION_PORTRAIT))
            value = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

        return value;
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

    public AppRTCClient getBinder() {
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
            appRtcClient = (AppRTCClient) iBinder;
            service = appRtcClient.getService();
            performanceAdapter.setPerformanceData(appRtcClient.getPerformance());
            bound = true;

            // after we have bound to the service, begin the connection
            appRtcClient.connectToRoom(AppRTCActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
            service = null;
            performanceAdapter.clearPerformanceData();
        }
    };

    public void startProgressDialog() {
        vsv.setBackgroundColor(Color.DKGRAY); // if it isn't already set, make the background color dark gray
        pd = new ProgressDialog(AppRTCActivity.this);
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
        disconnectAndExit();
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // Log |msg| and Toast about it.
    public void logAndToast(final int resID) {
        Log.d(TAG, getResources().getString(resID));
        this.runOnUiThread(new Runnable() {
            public void run() {
                if (logToast != null) {
                    logToast.cancel();
                }
                logToast = Toast.makeText(AppRTCActivity.this, resID, Toast.LENGTH_SHORT);
                logToast.show();
            }
        });
    }

    public void sendMessage(Request msg) {
        appRtcClient.sendMessage(msg);
    }

    // MessageHandler interface method
    // Called when the client connection is established
    public void onOpen() {
        if (!appRtcClient.isInitiator()) {
            return;
        }
        proxying = true;

        // set up ICE servers
        pcObserver.onIceServers(appRtcClient.getSignalingParams().iceServers);

        touchHandler.sendScreenInfoMessage();
        rotationHandler.initRotationUpdates();

        logAndToast(R.string.appRTC_toast_clientHandler_start);
        pcObserver.getPC().createOffer(sdpObserver, sdpMediaConstraints);
    }

    // MessageHandler interface method
    // Called when a message is sent from the server, and the SessionService doesn't consume it
    public boolean onMessage(Response data) {
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
            default:
                Log.e(TAG, "Unexpected protocol message of type " + data.getType().name());
        }
        return true;
    }

    // when authentication fails, or a session maxTimeout or idleTimeout message is received, stop the
    // AppRTCActivity, close the connection, and cause the ConnectionList activity to reconnect to this
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
                // TODO: currently, sending a BYE message causes the VM to close the connection... we don't want that
//                if (appRtcClient.getState() == STATE.RUNNING) {
//                    Request bye = Request.newBuilder().setType(RequestType.WEBRTC)
//                            .setWebrtcMsg(WebRTCMessage.newBuilder().setType(WebRTCType.BYE)).build();
//                    try {
//                        sendMessage(bye);
//                    } catch (Exception e) {
//                        // don't care
//                    }
//                }
                unbindService(serviceConnection);
            }

            // TODO: stopping service here, remove this eventually
            Intent intent = new Intent(this, SessionService.class);
            stopService(intent);

            pcObserver.quit();
            stopProgressDialog(); // prevent resource leak if we disconnect while the progress dialog is still up

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
                            // clear timed out session information from memory
                            dbHandler.clearSessionInfo(connectionInfo);
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
}
