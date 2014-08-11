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

package org.mitre.svmp.apprtc;

import android.net.Uri;
import android.os.Binder;
import android.util.Log;
import com.google.protobuf.InvalidProtocolBufferException;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.body.JSONObjectBody;

import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.net.SSLConfig;
import org.mitre.svmp.performance.PerformanceTimer;
import org.mitre.svmp.services.SessionService;
import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.auth.AuthData;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.*;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.common.StateMachine.STATE;

import java.io.IOException;
import java.util.Date;

/**
 * @author Joe Portner
 *
 * Negotiates signaling for chatting with apprtc.appspot.com "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * Now extended to act as a Binder object between a Service and an Activity.
 *
 * To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once that's done call sendMessage() and wait for the
 * registered handler to be called with received messages.
 */
public class AppRTCClient extends Binder implements Constants {
    private static final String TAG = AppRTCClient.class.getName();

    // service and activity objects
    private StateMachine machine;
    private SessionService service = null;
    private AppRTCActivity activity = null;

    // common variables
    private ConnectionInfo connectionInfo;
    private DatabaseHandler dbHandler;
    private boolean init = false; // switched to 'true' when activity first binds
    private boolean proxying = false; // switched to 'true' upon state machine change
    private AppRTCSignalingParameters signalingParams;

    // performance instrumentation
    private PerformanceTimer performance;

    // variables for networking
    private boolean useSSL;
    private AsyncHttpClient ahClient;
    private WebSocket webSocket;

    // STEP 0: NEW -> STARTED
    public AppRTCClient(SessionService service, StateMachine machine, ConnectionInfo connectionInfo) {
        this.service = service;
        this.machine = machine;
        machine.addObserver(service);
        this.connectionInfo = connectionInfo;

        this.dbHandler = new DatabaseHandler(service);
        this.performance = new PerformanceTimer(service, this, connectionInfo.getConnectionID());
        this.ahClient = AsyncHttpClient.getDefaultInstance();

        machine.setState(STATE.STARTED, 0);
    }

    // called from activity
    public void connectToRoom(AppRTCActivity activity) {
        this.activity = activity;
        machine.addObserver(activity);

        // we don't initialize the SocketConnector until the activity first binds; mitigates concurrency issues
        if (!init) {
            init = true;

            int error = 0;
            // determine whether we should use SSL from the EncryptionType integer
            useSSL = connectionInfo.getEncryptionType() == Constants.ENCRYPTION_SSLTLS;

            if (useSSL) {
                error = new SSLConfig(connectionInfo, service).apply(ahClient.getSSLSocketMiddleware());
            }

            if (error != 0)
                machine.setState(STATE.ERROR, error);
            else
                login();
        }
        // if the state is already running, we are reconnecting
        else if (machine.getState() == STATE.RUNNING) {
            activity.onOpen();
        }
    }

    // called from activity
    public void disconnectFromRoom() {
        machine.removeObserver(activity);
        this.activity = null;
    }

    public boolean isBound() {
        return this.activity != null;
    }

    public PerformanceTimer getPerformance() {
        return performance;
    }

    public AppRTCSignalingParameters getSignalingParams() {
        return signalingParams;
    }

    // called from AppRTCActivity
    public void changeToErrorState() {
        machine.setState(STATE.ERROR, R.string.appRTC_toast_connection_finish);
    }

    public void disconnect() {
        proxying = false;

        // we're disconnecting, update the database record with the current timestamp
        dbHandler.updateLastDisconnected(connectionInfo, new Date().getTime());
        dbHandler.close();

        performance.cancel(); // stop taking performance measurements

        // shut down the WebSocket if it's open
        if (webSocket != null)
            webSocket.close(); // TODO: is this right, or should we use end()?
    }

    public synchronized void sendMessage(Request msg) {
        if (proxying) {
            webSocket.send(msg.toByteArray());
        }
    }

    // STEP 1: STARTED -> AUTH, Authenticate with the SVMP login REST service
    public void login() {
        // attempt to get any existing auth data JSONObject that's in memory (e.g. made of user input such as password)
        JSONObject jsonObject = AuthData.getJSON(connectionInfo);
        if (jsonObject == null) {
            // there was no auth JSONObject in memory; see if we can construct one from a session token
            String sessionToken = dbHandler.getSessionToken(connectionInfo);
            if (sessionToken.length() > 0) {
                jsonObject = AuthData.makeJSON(connectionInfo, sessionToken);
            }
        }

        if (jsonObject == null) {
            Log.e(TAG, "login failed: jsonObject is null");
            machine.setState(STATE.ERROR, R.string.appRTC_toast_connection_finish); // TODO: specific error message
            return;
        }

        String proto = useSSL ? "https" : "http",
                // if we're changing our password, use a different API
                api = jsonObject.has("newPassword") ? "login" : "changePassword";
        Uri uri = Uri.parse(String.format("%s://%s:%d/%s",
                proto, connectionInfo.getHost(), connectionInfo.getPort(), api));
        AsyncHttpRequest loginReq = new AsyncHttpRequest(uri, "POST");
        loginReq.setBody(new JSONObjectBody(jsonObject));
        ahClient.executeJSONObject(loginReq, new AsyncHttpClient.JSONObjectCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse resp, JSONObject json) {
                int error = R.string.appRTC_toast_socketConnector_fail; // generic error message

                // generate a status code
                String host, port, token;
                host = port = token = null;
                if (e == null) {
                    try {
                        String type = json.getString("type");
                        if (type.equals("AUTH_OK")) {
                            // get session info
                            JSONObject sessionInfo = json.getJSONObject("sessionInfo");
                            token = sessionInfo.getString("token");
                            long expires = new Date().getTime() + (1000 * sessionInfo.getInt("maxLength"));
                            int gracePeriod = sessionInfo.getInt("gracePeriod");
                            dbHandler.updateSessionInfo(connectionInfo, token, expires, gracePeriod);

                            // get server info
                            host = json.getJSONObject("server").getString("host");
                            port = json.getJSONObject("server").getString("port");

                            // get webrtc info
                            signalingParams = AppRTCHelper.getParametersForRoom(json.getJSONObject("webrtc"));

                            if (signalingParams != null)
                                error = 0; // success
                        }
                        else if (type.equals("NEED_PASSWORD_CHANGE")) {
                            error = R.string.svmpActivity_toast_needPasswordChange;
                        }
                        else if (type.equals("AUTH_FAIL")) {
                            error = R.string.appRTC_toast_svmpAuthenticator_fail;
                        }
                        else if (type.equals("PASSWORD_CHANGE_FAIL")) {
                            error = R.string.appRTC_toast_svmpAuthenticator_passwordChangeFail;
                        }
                    } catch (JSONException ex) {
                        Log.e(TAG, "login received invalid response:", ex);
                        // TODO: specific error message
                    }
                }
                else {
                    Log.e(TAG, "login failed:", e);
                    // TODO: error case: the server expects TLS on but we have it off
                    // TODO: error case: the server expects TLS off but we have it on
                    // TODO: error case: the server's certificate isn't in our trust store
                    // TODO: error case: the server expects a certificate but we didn't provide one
                    // TODO: error case: our client certificate isn't in the server's trust store
                }

                // act on the status code
                if (error == 0) // success, start the next phase and connect to the SVMP proxy server
                    connect(host, port, token);
                else // fail with the appropriate error message
                    machine.setState(STATE.ERROR, error);
            }
        });
    }

    // STEP 2: AUTH -> CONNECTED, Connect to the SVMP proxy service
    public void connect(String host, String port, String token) {
        Log.d(TAG, "Socket connecting to " + connectionInfo.getHost() + ":" + connectionInfo.getPort());

        String proto = useSSL ? "wss" : "ws";

        Uri uri = Uri.parse(String.format("%s://%s:%s", proto, host, port));
        AsyncHttpRequest wsReq = new AsyncHttpRequest(uri, "GET"); // FIXME: is this supposed to be GET or POST?
        wsReq.addHeader("x-access-token", token);

        ahClient.websocket(wsReq, "svmp", new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception e, WebSocket socket) {
                if (e != null) {
                    Log.e(TAG, "Failed to connect to SVMP proxy:", e);
                    machine.setState(STATE.ERROR, R.string.appRTC_toast_socketConnector_fail);
                    return;
                }

//                socket.setStringCallback(new WebSocket.StringCallback() {
//                    public void onStringAvailable(String s) {
//                        // don't need this
//                    }
//                });

                Log.i(TAG, "WebSocket connected.");
                webSocket = socket;
                socket.setEndCallback(completedCallback);
                socket.setDataCallback(connectedCallback); // changes after receiving VMREADY
                machine.setState(STATE.CONNECTED, R.string.appRTC_toast_socketConnector_success); // AUTH -> CONNECTED
            }
        });
    }
    CompletedCallback completedCallback = new CompletedCallback() {
        @Override
        public void onCompleted(Exception e) {
            if (e != null) {
                if (proxying) {
                    // we haven't called disconnect(), this was an error; log this as an Error message and change state
                    changeToErrorState();
                    Log.e(TAG, "WebSocket disconnected:", e);
                }
                else // we called disconnect(), this was intentional; log this as an Info message
                    Log.i(TAG, "WebSocket disconnected.");
            }
        }
    };

    // STEP 3: CONNECTED -> RUNNING, Receive VMREADY message
    DataCallback connectedCallback = new ProtobufDataCallback() {
        @Override
        public void onResponse(Response data) {
            int error = R.string.appRTC_toast_connection_finish; // generic error message

            // generate a status code
            Response.ResponseType type = data.getType();
            if (type == Response.ResponseType.VMREADY)
                error = 0;
            else if (type == Response.ResponseType.AUTH && data.getAuthResponse().getType() == AuthResponse.AuthResponseType.AUTH_FAIL)
                error = R.string.appRTC_toast_svmpAuthenticator_fail;
            else if (type == Response.ResponseType.ERROR)
                error = R.string.appRTC_toast_svmpReadyWait_fail;

            // act on the status code
            if (error == 0) { // success
                webSocket.setDataCallback(runningCallback);
                machine.setState(STATE.RUNNING, R.string.appRTC_toast_svmpReadyWait_success); // CONNECTED -> RUNNING
                proxying = true;

                // callbacks to the service and activity to let them know the connection has started
                service.onOpen();
                if (isBound())
                    activity.onOpen();

                // start taking performance measurements
                performance.start();
            }
            else // fail with the appropriate error message
                machine.setState(STATE.ERROR, error);
        }
    };

    // we switch to this callback while we are in proxying mode (STATE: RUNNING)
    DataCallback runningCallback = new ProtobufDataCallback() {
        @Override
        public void onResponse(final Response data) {
            boolean consumed = service.onMessage(data);
            if (!consumed && isBound()) {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        activity.onMessage(data);
                    }
                });
            }
        }
    };

    // specialized DataCallback class for receiving and parsing Protobuf messages
    private class ProtobufDataCallback implements DataCallback {
        @Override
        public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
            try {
                Response data = Response.parseFrom(byteBufferList.getAllByteArray());
                Log.d(TAG, "Received incoming message object of type " + data.getType().name());
                onResponse(data);
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Unable to parse protobuf:", e);
                changeToErrorState();
            }
        }

        public void onResponse(Response data) {
            // override in child class
        }
    }
}
