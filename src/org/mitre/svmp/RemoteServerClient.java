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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.SocketFactory;

//import org.mitre.svmp.protocol.SVMPMessage;
import org.mitre.svmp.protocol.SVMPProtocol;

import android.net.SSLCertificateSocketFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Remote client using AsyncTask to create a background thread and keep socket alive
 *
 * @author Dave Bryson
 */
//public class RemoteServerClient extends AsyncTask<Void, SVMPMessage, Boolean> {
public class RemoteServerClient extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = "RemoteServerClient";
    private static final int BUFFER_SIZE = 8 * 1024;
    private OutputStream out = null;
    private InputStream in = null;
    private Socket socket;
    private String host;
    private int port;
    private Handler callback;
    private boolean running;

    private static boolean USE_SSL = true;
    // for testing, set true to disable server cert validity checking
    private static boolean SSL_DEBUG = false;

    public RemoteServerClient(final Handler callback, final String host, final int port)
        throws IOException, NoSuchAlgorithmException, KeyManagementException {
        this.host = host;
        this.port = port;
        this.callback = callback;

        SocketFactory sf;

        if (USE_SSL) {
            if (SSL_DEBUG)
                sf = SSLCertificateSocketFactory.getInsecure(0, null);
            else
                sf = SSLCertificateSocketFactory.getDefault(0, null);
        } else {
            sf = SocketFactory.getDefault();
        }

//        SocketAddress sockaddr = new InetSocketAddress(this.host, this.port);
//        socket.connect(sockaddr, 5000); //5 second connection timeout

        socket = sf.createSocket(host, port);

        Log.d(TAG, "Socket connected to " + host + ":" + port);
        out = socket.getOutputStream();
        out.flush();
        in = socket.getInputStream();
        Log.d(TAG, "Object Streams created");
        this.running = true;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Log.e(TAG, "connected...");
        try {
            while (this.running && !isCancelled() && socket.isConnected()) {
            	Log.d(TAG, "Waiting for incoming message");
            	SVMPProtocol.Response m = SVMPProtocol.Response.parseDelimitedFrom(in);
            	Log.d(TAG, "Received incoming message object of type " + m );
                if(m != null){
                    final Message message = Message.obtain(this.callback);
                    message.obj = m;
                    message.sendToTarget();
                }
            }
        } catch (Exception e) {
        	Log.i(TAG, "Server connection disconnected.");
//        	StringWriter sw = new StringWriter();
//        	e.printStackTrace(new PrintWriter(sw));
//            Log.e(TAG, "Error on socket: " + e.getMessage() + "\n" + e.toString() + "\n" + sw.toString());           
//            throw new RuntimeException(e);
        }
        // Cancel or closed
        Log.e(TAG, "Stopping client, running is "+running);
        return running;
    }
    
    public synchronized boolean sendMessage(SVMPProtocol.Request msg) {
    	if (socket != null && socket.isConnected()) {
    		try {
				msg.writeDelimitedTo(out);
				return true;
			} catch (IOException e) {
				Log.e(TAG,"IOException in sendMessage " + e.getMessage());
			}
    	}
    	return false;
    }
    
    /*
    public synchronized boolean sendEvent(SVMPMessage msg) {
        if (socket != null && socket.isConnected()) {
        	SVMPProtocol.Request.Builder req = SVMPProtocol.Request.newBuilder();
        	SVMPProtocol.Proxy.Builder p = SVMPProtocol.Proxy.newBuilder();
          	req.setType(RequestType.RAWINPUTPROXY);
			p.setType(ServiceType.INPUT);			
          	
        	try {
              	ByteArrayOutputStream bs = new ByteArrayOutputStream();
              	ObjectOutputStream os = new ObjectOutputStream(bs);
              	
				os.writeObject(msg);
				os.flush();
				byte[] bytes = bs.toByteArray();

				p.setData(ByteString.copyFrom(bytes));
				req.setProxy(p);
				req.build().writeDelimitedTo(out);
        	} catch (IOException e) {				
				Log.e(TAG,"IOException in sendEvent " + e.getMessage());
			}        	        	        	
            return true;
        }

        return false;
    }
    */

    public synchronized void stop() {
    	Log.e(TAG,"received a stop event, running is now set to false");
        this.running = false;
        if(this.cancel(true))
        	Log.e(TAG,"cancel succesful");
        else
        	Log.e(TAG,"Problem with canceling connection..");
        
        // forcably kill connection
        try {
            Log.d(TAG, "Forcing Closing connection");
            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
        }
    }
    
    public synchronized Boolean status() {
    	return this.running;
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "Cancelled.");
        try {
            Log.d(TAG, "Closing connection");
            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            Log.i(TAG, "onPostExecute: Completed.");
        } else {
            // TODO: Send Back to Message to Activity
            Log.i(TAG, "onPostExecute: Could not connect to server.");
        }
    }

}
