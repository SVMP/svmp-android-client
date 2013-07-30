/*
Copyright 2013 The MITRE Corporation, All Rights Reserved.

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
package org.mitre.svmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import org.mitre.svmp.client.NetIntentsClient;
import org.mitre.svmp.protocol.SVMPProtocol;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Remote client keeping all socket activity on background threads
 *
 * @author Dave Bryson
 * @author Andy Pyles
 * @author Dave Keppler
 */
//public class RemoteServerClient extends AsyncTask<Void, SVMPMessage, Boolean> {
public class RemoteServerClient extends Thread implements Constants {

    private static final String TAG = RemoteServerClient.class.getName();

    private static OutputStream out = null;
    private InputStream in = null;
    private static Socket socket;
    private String host;
    private int port;

    private static Handler send_handler;
    private ListenThread lthread;
    private Handler callback;
    private Context parent;
    private boolean running = false;
    private boolean useSsl;
    private boolean sslDebug;

    public RemoteServerClient(final Handler callback, final Context parent,final String host, final int port, final int encryptionType) {
        this.host = host;
        this.port = port;
        this.callback = callback;
        this.parent = parent;
        // determine both booleans from the EncryptionType integer
        useSsl = (encryptionType == ENCRYPTION_SSLTLS || encryptionType == ENCRYPTION_SSLTLS_UNTRUSTED);
        sslDebug = (encryptionType == ENCRYPTION_SSLTLS_UNTRUSTED);
    }

    private void socketConnect() throws UnknownHostException, IOException {
        SocketFactory sf;

        Log.d(TAG, "Socket connecting to " + host + ":" + port);

        if (useSsl) {
            if (sslDebug)
                sf = SSLCertificateSocketFactory.getInsecure(0, null);
            else
                sf = SSLCertificateSocketFactory.getDefault(0, null);
        } else {
            sf = SocketFactory.getDefault();
        }

        socket = sf.createSocket(host, port);

       

        out = socket.getOutputStream();
        in = socket.getInputStream();
    }

    @Override
    public void run() {
        try {
            Looper.prepare();
            send_handler = new Handler();

            socketConnect();

            lthread = new ListenThread(callback,parent,in);
            lthread.start();                      
            // fire off empty message to send an auth request
            final Message message = Message.obtain(callback);         
            message.sendToTarget();
            running = true;
            Log.i(TAG, "!!! Connected successfully");
            Looper.loop();                   

            Log.i(TAG, "Send thread exited cleanly");
        } catch (Throwable t) {
            Log.e(TAG, "Send thread halted due to an error", t);
            if(lthread != null)
                lthread.stop();
        }
    }

    public synchronized void Stop() {
        send_handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Terminating connection");
                    lthread.Stop();
                    in.close();
                    out.close();
                    socket.close();
                    running = false;
                    //empty message for now.
                    
                } catch (Exception e) {
                    Log.d(TAG, "Exception: " + Log.getStackTraceString(e));
                }
                Looper.myLooper().quit();
            }
        });
    }

    public synchronized Boolean status() {
        return running;
    }

    public static synchronized void sendMessage(final SVMPProtocol.Request msg) {
    	if(send_handler != null)
        send_handler.post(new Runnable() {
            @Override
            public void run() {
                if (socket != null && socket.isConnected()) {
                    try {
                        Log.i(TAG,"Writing message to VM...");
                        msg.writeDelimitedTo(out);
                    } catch (IOException e) {
                        Log.e(TAG,"IOException in sendMessage " + e.getMessage());
                    }
                }
            }
    	});
    }
    
    public String getLocalIP() {
//      if (socket.isConnected())
        return socket.getLocalAddress().getHostAddress();
//      else
//          return null
    }

    private class ListenThread extends Thread {
        private Handler callback;
        private InputStream in;
        private Context parent;

        boolean running = true;

        public ListenThread(Handler callback, Context parent,InputStream in) throws IOException {
            this.callback = callback;
            this.parent = parent;
            this.in = in;
        }

        @Override
        public void run() {
            try {
                Log.i(TAG, "Server connection receive thread starting");
                while (running && in != null) {
                    Log.d(TAG, "Waiting for incoming message");
                    SVMPProtocol.Response m = SVMPProtocol.Response.parseDelimitedFrom(in);
                    Log.d(TAG, "Received incoming message object of type " + m);

                    if (m != null) {
                        final Message message = Message.obtain(callback);
                        message.obj = m;
                        message.sendToTarget();
                    }
                }
                Log.i(TAG, "Server connection receive thread exiting");
            } catch (Exception e) {
                running = false;
                Log.i(TAG, "Server connection disconnected.");
            }
        }

        public synchronized void Stop() {
            running = false;
        }
    }
}
