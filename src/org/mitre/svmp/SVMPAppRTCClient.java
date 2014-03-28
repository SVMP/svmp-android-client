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

package org.mitre.svmp;

import android.os.AsyncTask;
import android.os.Binder;
import android.util.Log;
import de.duenndns.ssl.MemorizingTrustManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.auth.AuthData;
import org.mitre.svmp.auth.SVMPKeyManager;
import org.mitre.svmp.auth.module.CertificateModule;
import org.mitre.svmp.client.R;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.StateMachine.STATE;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Negotiates signaling for chatting with apprtc.appspot.com "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once that's done call sendMessage() and wait for the
 * registered handler to be called with received messages.
 */
public class SVMPAppRTCClient extends Binder implements Constants {
    private static final String TAG = SVMPAppRTCClient.class.getName();

    private StateMachine machine;
    private SessionService service = null;
    private AppRTCDemoActivity activity = null;
    private SVMPChannelClient.MessageHandler gaeHandler;
    private IceServersObserver iceServersObserver;

    // These members are only read/written under sendQueue's lock.
    private BlockingQueue<SVMPProtocol.Request> sendQueue = new LinkedBlockingQueue<SVMPProtocol.Request>();
    private AppRTCSignalingParameters appRTCSignalingParameters;

    private ConnectionInfo connectionInfo;
    private DatabaseHandler dbHandler;

    private Socket svmpSocket;
    private InputStream socketIn;
    private OutputStream socketOut;
    private boolean proxying = false;

    private SocketSender sender = null;
    private SocketListener listener = null;

    /**
     * Callback fired once the room's signaling parameters specify the set of
     * ICE servers to use.
     */
    public static interface IceServersObserver {
        public void onIceServers(List<PeerConnection.IceServer> iceServers);
    }

    public SVMPAppRTCClient(SessionService service, StateMachine machine, ConnectionInfo connectionInfo) {
        this.service = service;
        this.machine = machine;
        machine.addObserver(service);
        this.connectionInfo = connectionInfo;
    }

    public void connectToRoom(AppRTCDemoActivity activity, SVMPChannelClient.MessageHandler gaeHandler,
            IceServersObserver iceServersObserver) {
        this.activity = activity;
        machine.addObserver(activity);
        this.gaeHandler = gaeHandler;
        this.iceServersObserver = iceServersObserver;
        this.dbHandler = new DatabaseHandler(activity);

        (new SocketConnector()).execute();
    }

    public SessionService getService() {
        // Return this instance of SessionService so clients can call public methods
        return this.service;
    }

    /**
     * Disconnect from the SVMP proxy channel.
     *
     * @throws IOException
     */
    public void disconnect() {
        proxying = false;

        // we're disconnecting, update the database record with the current timestamp
        dbHandler.updateLastDisconnected(connectionInfo, new Date().getTime());

        if (dbHandler != null)
            dbHandler.close();
        if (sender != null)
            sender.cancel(true);
        if (listener != null)
            listener.cancel(true);
        try {
            if (socketIn != null)
                socketIn.close();
        } catch(IOException e) {
            Log.e(TAG, "Exception closing InputStream: " + e.getMessage());
        }
        try {
            if (socketOut != null)
                socketOut.close();
        } catch(IOException e) {
            Log.e(TAG, "Exception closing OutputStream: " + e.getMessage());
        }
        try {
            if (svmpSocket != null && !svmpSocket.isClosed())
                svmpSocket.close();
        } catch(IOException e) {
            Log.e(TAG, "Exception closing Socket: " + e.getMessage());
        }
    }

    private void startProxying() {
        proxying = true;

        sender = new SocketSender();
        listener = new SocketListener();

        sender.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        listener.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isProxying() {
        return proxying;
    }

    /**
     * Queue a message for sending to the room's channel and send it if already
     * connected (other wise queued messages are drained when the channel is
     * eventually established).
     */
    public synchronized void sendMessage(String msg) {
        WebRTCMessage.Builder rtcmsg = WebRTCMessage.newBuilder();
        rtcmsg.setJson(msg);

        sendMessage(Request.newBuilder()
                .setType(RequestType.WEBRTC)
                .setWebrtcMsg(rtcmsg)
                .build());
    }

    public synchronized void sendMessage(Request msg) {
        if (proxying)
            sendQueue.add(msg);
    }

    public boolean isInitiator() {
        return appRTCSignalingParameters.initiator;
    }

    public MediaConstraints pcConstraints() {
        return appRTCSignalingParameters.pcConstraints;
    }

    // Struct holding the signaling parameters of an AppRTC room.
    private class AppRTCSignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final boolean initiator;
        public final MediaConstraints pcConstraints;
        public final MediaConstraints videoConstraints;

        public AppRTCSignalingParameters(
                List<PeerConnection.IceServer> iceServers,
                boolean initiator, MediaConstraints pcConstraints,
                MediaConstraints videoConstraints) {
            this.initiator = initiator;
            this.iceServers = iceServers;
            this.pcConstraints = pcConstraints;
            this.videoConstraints = videoConstraints;
        }
    }

    // AsyncTask that converts an AppRTC room URL into the set of signaling
    // parameters to use with that room.
    private class VideoParameterGetter
            extends AsyncTask<Void, Void, AppRTCSignalingParameters> {

        @Override
        protected AppRTCSignalingParameters doInBackground(Void... params) {
            AppRTCSignalingParameters value = null;
            try {
                // send video info request
                Request.Builder req = Request.newBuilder();
                req.setType(RequestType.VIDEO_PARAMS);
                req.build().writeDelimitedTo(socketOut);

                // get video info response
                Response resp = Response.parseDelimitedFrom(socketIn);

                // parse it and populate a SignalingParams
                if (resp != null && resp.getType() == ResponseType.VIDSTREAMINFO || resp.hasVideoInfo())
                    value = getParametersForRoom(resp.getVideoInfo());

            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return value;
        }

        @Override
        protected void onPostExecute(AppRTCSignalingParameters params) {
            if (params != null) {
                machine.setState(STATE.RUNNING, R.string.appRTC_toast_videoParameterGetter_success); // READY -> RUNNING
                startProxying();

                appRTCSignalingParameters = params;
                iceServersObserver.onIceServers(appRTCSignalingParameters.iceServers);
                gaeHandler.onOpen();
            }
            else {
                machine.setState(STATE.ERROR, R.string.appRTC_toast_videoParameterGetter_fail); // READY -> ERROR
            }
        }

        private AppRTCSignalingParameters getParametersForRoom(VideoStreamInfo info) {
            MediaConstraints pcConstraints = constraintsFromJSON(info.getPcConstraints());
            Log.d(TAG, "pcConstraints: " + pcConstraints);

            MediaConstraints videoConstraints = constraintsFromJSON(info.getVideoConstraints());
            Log.d(TAG, "videoConstraints: " + videoConstraints);

            LinkedList<PeerConnection.IceServer> iceServers = iceServersFromPCConfigJSON(info.getIceServers());

            return new AppRTCSignalingParameters(iceServers, true, pcConstraints, videoConstraints);

        }

        private MediaConstraints constraintsFromJSON(String jsonString) {
            try {
                MediaConstraints constraints = new MediaConstraints();
                JSONObject json = new JSONObject(jsonString);
                JSONObject mandatoryJSON = json.optJSONObject("mandatory");
                if (mandatoryJSON != null) {
                    JSONArray mandatoryKeys = mandatoryJSON.names();
                    if (mandatoryKeys != null) {
                        for (int i = 0; i < mandatoryKeys.length(); ++i) {
                            String key = (String) mandatoryKeys.getString(i);
                            String value = mandatoryJSON.getString(key);
                            constraints.mandatory.add(
                                    new MediaConstraints.KeyValuePair(key, value));
                        }
                    }
                }
                JSONArray optionalJSON = json.optJSONArray("optional");
                if (optionalJSON != null) {
                    for (int i = 0; i < optionalJSON.length(); ++i) {
                        JSONObject keyValueDict = optionalJSON.getJSONObject(i);
                        String key = keyValueDict.names().getString(0);
                        String value = keyValueDict.getString(key);
                        constraints.optional.add(
                                new MediaConstraints.KeyValuePair(key, value));
                    }
                }
                return constraints;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(
            String pcConfig) {
        try {
            JSONObject json = new JSONObject(pcConfig);
            JSONArray servers = json.getJSONArray("iceServers");
            LinkedList<PeerConnection.IceServer> ret =
                    new LinkedList<PeerConnection.IceServer>();
            for (int i = 0; i < servers.length(); ++i) {
                JSONObject server = servers.getJSONObject(i);
                String url = server.getString("url");
                String credential =
                        server.has("credential") ? server.getString("credential") : "";
                ret.add(new PeerConnection.IceServer(url, "", credential));
            }
            return ret;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Connect to the SVMP server
    private class SocketConnector extends AsyncTask<Void, Void, Integer> {

        private Request authRequest;

        @Override
        protected Integer doInBackground(Void... params) {
            int returnVal = R.string.appRTC_toast_socketConnector_fail; // resID for return message
            try {
                // get the auth request, which is either constructed from a session token or made up of auth input (password, etc)
                String sessionToken = dbHandler.getSessionToken(connectionInfo);
                if (sessionToken.length() > 0)
                    authRequest = AuthData.makeRequest(connectionInfo, sessionToken);
                else
                    // get the auth data req it's removed from memory
                    authRequest = AuthData.getRequest(connectionInfo);

                socketConnect();
                if (svmpSocket instanceof SSLSocket) {
                    SSLSocket sslSocket = (SSLSocket) svmpSocket;
                    sslSocket.startHandshake(); // starts the handshake to verify the cert before continuing
                }
                socketOut = svmpSocket.getOutputStream();
                socketIn = svmpSocket.getInputStream();
                returnVal = 0;
            } catch (SSLHandshakeException e) {
                String msg = e.getMessage();
                if (msg.contains("SSL handshake terminated") && msg.contains("certificate unknown")) {
                    // our client certificate isn't in the server's trust store
                    Log.e(TAG, "Untrusted client certificate!");
                    returnVal = R.string.appRTC_toast_socketConnector_failUntrustedClient;
                } else if (msg.contains("java.security.cert.CertPathValidatorException")) {
                    // the server's certificate isn't in our trust store
                    Log.e(TAG, "Untrusted server certificate!");
                    returnVal = R.string.appRTC_toast_socketConnector_failUntrustedServer;
                } else if (msg.contains("alert bad certificate")) {
                    // the server expects a certificate but we didn't provide one
                    Log.e(TAG, "Server requires client certificate!");
                    returnVal = R.string.appRTC_toast_socketConnector_failClientCertRequired;
                } else {
                    Log.e(TAG, "Error during SSL handshake: " + e.getMessage());
                    returnVal = R.string.appRTC_toast_socketConnector_failSSLHandshake;
                }
            } catch (SSLException e) {
                if (e.getMessage().contains("I/O error during system call, Connection reset by peer")) {
                    // connection failed, we tried to connect using SSL but proxy's SSL is turned off
                    returnVal = R.string.appRTC_toast_socketConnector_failSSL;
                } else {
                    Log.e(TAG, e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return returnVal;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 0) {
                // the client will soon be independent of the activity, this conditional will be obsolete
                if (activity.isFinishing()) {
                    Log.d(TAG, "Client exited before socketConnect finished, shutting down...");
                    disconnect();
                } else {
                    machine.setState(STATE.CONNECTED, R.string.appRTC_toast_socketConnector_success); // STARTED -> CONNECTED
                    new SVMPAuthenticator().execute(authRequest);
                }
            } else {
                machine.setState(STATE.ERROR, result); // STARTED -> ERROR
            }
        }

        private void socketConnect() throws IOException,
                KeyStoreException, NoSuchAlgorithmException, CertificateException, KeyManagementException,
                UnrecoverableKeyException {
            // determine whether we should use SSL from the EncryptionType integer
            boolean useSsl = connectionInfo.getEncryptionType() == ENCRYPTION_SSLTLS;
            // find out if we should use the MemorizingTrustManager instead of the system trust store (set in Preferences)
            boolean useMTM = Utility.getPrefBool(activity,
                    R.string.preferenceKey_connection_useMTM,
                    R.string.preferenceValue_connection_useMTM);
            // determine whether we should use client certificate authentication
            boolean useCertificateAuth = API_ICS &&
                    (connectionInfo.getAuthType() & CertificateModule.AUTH_MODULE_ID) == CertificateModule.AUTH_MODULE_ID;

            SocketFactory sf;

            Log.d(TAG, "Socket connecting to " + connectionInfo.getHost() + ":" + connectionInfo.getPort());

            if (useSsl) {
                // set up key managers
                KeyManager[] keyManagers = null;
                // if certificate authentication is enabled, use a key manager with the provided alias
                if (useCertificateAuth) {
                    keyManagers = new KeyManager[]{new SVMPKeyManager(service, connectionInfo.getCertificateAlias())};
                }

                // set up trust managers
                TrustManager[] trustManagers = null;

                KeyStore localTrustStore = KeyStore.getInstance("BKS");
                InputStream in = service.getResources().openRawResource(R.raw.client_truststore);
                localTrustStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(localTrustStore);

                // 1) If "res/raw/client_truststore.bks" is not empty, use it as the pinned cert trust store (default is empty)
                // 2) Otherwise, if the "Show certificate dialog" developer preference is enabled, use that (default is disabled)
                // 3) Otherwise, use the default system trust store, consists of normal trusted Android CA certs
                if (localTrustStore.size() > 0) {
                    // this means that "res/raw/client_truststore.bks" has been replaced with a trust store that is not empty
                    // we will use that "pinned" store to check server certificate trust
                    Log.d(TAG, "socketConnect: Using static BKS trust store to check server cert trust");
                    trustManagers = trustManagerFactory.getTrustManagers();
                } else if (useMTM) {
                    // by default useMTM is false ("Show certificate dialog" in developer preferences)
                    // this creates a certificate dialog to decide what to do with untrusted certificates, instead of flat-out rejecting them
                    Log.d(TAG, "socketConnect: Static BKS trust store is empty but MTM is enabled, using MTM to check server cert trust");
                    trustManagers = MemorizingTrustManager.getInstanceList(service);
                } else {
                    Log.d(TAG, "socketConnect: Static BKS trust store is empty and MTM is disabled, using system trust store to check server cert trust");
                    // leaving trustManagers null accomplishes this
                }

                SSLContext sslcontext = SSLContext.getInstance("TLS");
                sslcontext.init(keyManagers, trustManagers, new SecureRandom());
                SSLSocket socket = (SSLSocket) sslcontext.getSocketFactory().createSocket(connectionInfo.getHost(), connectionInfo.getPort());
                socket.setEnabledCipherSuites(ENABLED_CIPHERS);
                socket.setEnabledProtocols(ENABLED_PROTOCOLS);
                svmpSocket = socket;
            } else {
                sf = SocketFactory.getDefault();
                svmpSocket = sf.createSocket(connectionInfo.getHost(), connectionInfo.getPort());
            }
            svmpSocket.setTcpNoDelay(true);
        }
    }

    // Perform authentication request/response
    private class SVMPAuthenticator extends AsyncTask<Request, Void, Integer> {

        @Override
        protected Integer doInBackground(Request... request) {
            int returnVal = R.string.appRTC_toast_svmpAuthenticator_fail;

            if (svmpSocket.isConnected() && request[0] != null) {
                try {
                    // send authentication request
                    request[0].writeDelimitedTo(socketOut);

                    // get response
                    Response resp = Response.parseDelimitedFrom(socketIn);
                    if (resp != null && resp.getType() == ResponseType.AUTH) {
                        AuthResponse authResponse = resp.getAuthResponse();
                        if (authResponse.getType() == AuthResponse.AuthResponseType.AUTH_OK) {
                            // we authenticated successfully, check if we received session information
                            if (authResponse.hasSessionInfo()) {
                                SessionInfo sessionInfo = authResponse.getSessionInfo();
                                String token = sessionInfo.getToken();
                                long expires = new Date().getTime() + (1000 * sessionInfo.getMaxLength());
                                int gracePeriod = sessionInfo.getGracePeriod();
                                dbHandler.updateSessionInfo(connectionInfo, token, expires, gracePeriod);
                            }

                            returnVal = 0; // success
                        }
                        // got an AuthResponse with a type of AUTH_FAIL
                    } else if (resp == null)
                        returnVal = R.string.appRTC_toast_svmpAuthenticator_interrupted;

                    // should be an AuthResponse with a type of AUTH_FAIL, but fail anyway if it isn't
                } catch (IOException e) {
                    // client isn't using encryption, server is
                    if (e.getMessage().equals("Protocol message contained an invalid tag (zero)."))
                        returnVal = R.string.appRTC_toast_socketConnector_failSSL;
                    else
                        Log.e(TAG, e.getMessage());
                }
            }
            return returnVal;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 0) {
                machine.setState(STATE.AUTH, R.string.appRTC_toast_svmpAuthenticator_success); // CONNECTED -> AUTH
                // auth succeeded, wait for VMREADY
                (new SVMPReadyWait()).execute();
            } else {
                // authentication failed, handle appropriately
                machine.setState(STATE.ERROR, result); // CONNECTED -> ERROR
            }
        }
    }

    // AsynTask that waits for the VMREADY message from the SVMP server
    private class SVMPReadyWait extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            // wait for VMREADY
            Response resp;
            try {
                resp = Response.parseDelimitedFrom(socketIn);
                if (resp != null && resp.getType() == ResponseType.VMREADY)
                    return true;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                machine.setState(STATE.READY, R.string.appRTC_toast_svmpReadyWait_success); // AUTH -> READY
                // auth succeeded, get room parameters
                (new VideoParameterGetter()).execute();
            } else {
                machine.setState(STATE.ERROR, R.string.appRTC_toast_svmpReadyWait_fail); // AUTH -> ERROR
            }
        }
    }

    // Send loop thread for when we're in proxying mode
    private class SocketSender extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            Log.i(TAG, "Server connection send thread starting");
            while (proxying && svmpSocket != null && svmpSocket.isConnected() && socketOut != null) {
                try {
                    // too noisy to leave enabled
                    //Log.d(TAG,"Writing message to VM...");
                    sendQueue.take().writeDelimitedTo(socketOut);
                } catch (Exception e) {
                    Log.e(TAG, "Exception in sendMessage " + e.getMessage());
                }
            }
            return null;
        }
    }

    // Receive loop thread for when we're in proxying mode
    private class SocketListener extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Log.i(TAG, "Server connection receive thread starting");
                while (proxying && svmpSocket != null && svmpSocket.isConnected() && socketIn != null) {
                    Log.d(TAG, "Waiting for incoming message");
                    final Response data = Response.parseDelimitedFrom(socketIn);
                    Log.d(TAG, "Received incoming message object of type " + data.getType().name());

                    if (data != null) {
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                gaeHandler.onMessage(data);
                            }
                        });
                    }
                }
                Log.i(TAG, "Server connection receive thread exiting");
            } catch (Exception e) {
                proxying = false;
                Log.i(TAG, "Server connection disconnected.");
            }
            return null;
        }
    }
}
