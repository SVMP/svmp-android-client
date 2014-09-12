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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import org.json.JSONObject;
import org.mitre.svmp.apprtc.*;
import org.mitre.svmp.client.*;
import org.mitre.svmp.common.*;
import org.mitre.svmp.performance.PerformanceAdapter;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.services.SessionService;
import org.webrtc.*;
import org.mitre.svmp.common.StateMachine.STATE;

/**
 * @author Joe Portner
 * Base activity for establishing an AppRTC connection
 */
public class AppRTCActivity extends Activity implements StateObserver, MessageHandler, Constants {

    private static final String TAG = AppRTCActivity.class.getName();

    protected AppRTCClient appRtcClient;
    protected PerformanceAdapter performanceAdapter;
    private boolean bound = false;

    private Toast logToast;

    // Synchronize on quit[0] to avoid teardown-related crashes.
    private final Boolean[] quit = new Boolean[]{false};

    protected DatabaseHandler dbHandler;
    protected ConnectionInfo connectionInfo;

    protected boolean proxying = false; // if this is true, we have finished the handshakes and the connection is running
    private ProgressDialog pd;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    // called from PCObserver
    public MediaConstraints getPCConstraints() {
        MediaConstraints value = null;
        if (appRtcClient != null)
            value = appRtcClient.getSignalingParams().pcConstraints;
        return value;
    }

    public void changeToErrorState() {
        if (appRtcClient != null)
            appRtcClient.changeToErrorState();
    }

    protected void connectToRoom() {
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
            performanceAdapter.setPerformanceData(appRtcClient.getPerformance());
            bound = true;

            // after we have bound to the service, begin the connection
            appRtcClient.connectToRoom(AppRTCActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    protected void startProgressDialog() {
        pd = new ProgressDialog(AppRTCActivity.this);
        pd.setCanceledOnTouchOutside(false);
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
        if (proxying)
            disconnectAndExit();
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

    // called from PCObserver, SDPObserver, RotationHandler, and TouchHandler
    public void sendMessage(Request msg) {
        if (appRtcClient != null)
            appRtcClient.sendMessage(msg);
    }

    // MessageHandler interface method
    // Called when the client connection is established
    public void onOpen() {
        proxying = true;
        logAndToast(R.string.appRTC_toast_clientHandler_start);
    }

    // MessageHandler interface method
    // Called when a message is sent from the server, and the SessionService doesn't consume it
    public boolean onMessage(Response data) {
        switch (data.getType()) {
            case AUTH:
                if (data.hasAuthResponse()) {
                    switch (data.getAuthResponse().getType()) {
                        case SESSION_MAX_TIMEOUT:
                            needAuth(R.string.svmpActivity_toast_sessionMaxTimeout, false);
                            break;
                    }
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
    public void needAuth(int messageResID, boolean passwordChange) {
        // clear timed out session information from memory
        dbHandler.clearSessionInfo(connectionInfo);
        // send a result message to the calling activity so it will show the authentication dialog again
        Intent intent = new Intent();
        intent.putExtra("connectionID", connectionInfo.getConnectionID());
        if (messageResID > 0)
            logAndToast(messageResID);
        setResult(passwordChange ? SvmpActivity.RESULT_NEEDPASSWORDCHANGE : SvmpActivity.RESULT_NEEDAUTH, intent);

        disconnectAndExit();
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    protected void disconnectAndExit() {
        proxying = false;
        synchronized (quit[0]) {
            if (quit[0]) {
                return;
            }
            quit[0] = true;

            // allow child classes to clean up their components
            onDisconnectAndExit();

            // Unbind from the service
            if (bound) {
                if (SessionService.getState() == STATE.RUNNING) {
                    JSONObject json = new JSONObject();
                    AppRTCHelper.jsonPut(json, "type", "bye");
                    try {
                        appRtcClient.sendMessage(AppRTCHelper.makeWebRTCRequest(json));
                    } catch (Exception e) {
                        // don't care
                    }
                }
                unbindService(serviceConnection);
                bound = false;
                appRtcClient.disconnectFromRoom();
                appRtcClient = null;
                performanceAdapter.clearPerformanceData();
            }

            stopProgressDialog(); // prevent resource leak if we disconnect while the progress dialog is still up

            // if the useBackground preference is unchecked, stop the session service before finishing
            boolean useBackground = Utility.getPrefBool(this, R.string.preferenceKey_connection_useBackground, R.string.preferenceValue_connection_useBackground);
            if (!useBackground)
                stopService(new Intent(this, SessionService.class));

            if (!isFinishing())
                finish();
        }
    }

    // override in child classes
    protected void onDisconnectAndExit() {
    }

    public boolean isConnected() {
        return proxying;
    }

    public void onStateChange(STATE oldState, STATE newState, int resID) {
        boolean exit = false;

        switch(newState) {
            case CONNECTED:
                break;
            case AUTH:
                break;
            case RUNNING:
                break;
            case ERROR:
                // we are in an error state, check the previous state and act appropriately
                switch(oldState) {
                    case STARTED: // failed to authenticate and transition to AUTH
                    case CONNECTED: // failed to receive ready message and transition to RUNNING (can fail auth to proxy)
                        if (resID == R.string.appRTC_toast_svmpAuthenticator_fail) {
                            // our authentication was rejected, exit and bring up the auth prompt when the connection list resumes
                            needAuth(resID, false);
                        } else if (resID == R.string.svmpActivity_toast_needPasswordChange
                                || resID == R.string.appRTC_toast_svmpAuthenticator_passwordChangeFail) {
                            needAuth(resID, true);
                        }
                        // otherwise, we had an SSL error, display the failure message and return to the connection list
                        break;
                    case AUTH: // failed to connect the WebSocket and transition to CONNECTED
                        // the socket connection failed, display the failure message and return to the connection list
                        break;
                    case RUNNING: // failed after already running
                        break;
                }

                exit = true;
                break;
            default:
                break;
        }

        // if the state change included a message, log it and display a toast popup message
        if (resID > 0 && !quit[0])
            logAndToast(resID);

        if (exit)
            // finish this activity and return to the connection list
            disconnectAndExit();
    }
}
