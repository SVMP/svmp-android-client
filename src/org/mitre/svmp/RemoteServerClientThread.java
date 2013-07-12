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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.SocketFactory;

import org.mitre.svmp.protocol.SVMPProtocol;

import android.net.SSLCertificateSocketFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/*
 * Background threads for Sending and Receiving.
 * Main thread for sending.
 */
public class RemoteServerClientThread extends Thread{

	private static final String TAG = RemoteServerClientThread.class.getSimpleName();
	//private Handler rcv_handler;
	private Handler snd_handler;
	private String host;
	private int port;
	private Socket socket;
	private boolean USE_SSL=false;
	private boolean SSL_DEBUG=true; // hard code this for now
	
	private Handler callback;
	
	private boolean running;
	
	private ListenThread  lthread= null;
	
	private OutputStream out = null;
    private InputStream in = null;
	
    private void SSLSetup()
    		throws IOException, NoSuchAlgorithmException, KeyManagementException {
    	SocketFactory sf;

        if (USE_SSL) {
            if (SSL_DEBUG)
                sf = SSLCertificateSocketFactory.getInsecure(0, null);
            else
                sf = SSLCertificateSocketFactory.getDefault(0, null);
        } else {
            sf = SocketFactory.getDefault();
        }
        socket = sf.createSocket(host, port);
    }
    
 
    public RemoteServerClientThread(final Handler callback, final String host,final int port)
    		throws IOException {
    	this.host=host;
    	this.port=port;
    	this.callback = callback;  	        
    	Log.d(TAG, "Object Streams created");
    	this.running = true;
    	
    }

    @Override
    public void run() {
    	try{

    		Looper.prepare();
    		snd_handler = new Handler();
    		SocketAddress sockaddr = new InetSocketAddress(this.host, this.port);
    		socket = new Socket();
    		try {
    			Log.d(TAG, "Socket connecting to " + host + ":" + port);
    			//socket.connect(sockaddr, 5000); // 5 second timeout
    			SSLSetup();
    		} catch (Exception e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} //5 second connection timeout
    		out = socket.getOutputStream();
    		out.flush();
    		in = socket.getInputStream();
    		lthread = new ListenThread(callback,in);
    		lthread.startListening();
    		running = true;
    	} catch (Throwable t) {
    		Log.e(TAG, "Send Thread halted due to an error", t);
    	} 

    }

    public synchronized void Stop() {
    	snd_handler.post(new Runnable() {
    		@Override
    		public void run() {
    			try {
    				Log.d(TAG, "Terminating connection");
    				in.close();
    				out.close();
    				socket.close();	
    				running=false;
    			} catch (Exception e) {
    				Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
    			}				
    			Looper.myLooper().quit();
    		}

    	});
    }
    
    public synchronized Boolean status() {
    	return this.running;
    }
    
    // Send message Within send thread context 
    public synchronized void sendMessage(final SVMPProtocol.Request msg) {
    	snd_handler.post(new Runnable() {
    		@Override
    		public void run() {
    			if (socket != null && socket.isConnected()) {
    				try {
    					msg.writeDelimitedTo(out);    					
    				} catch (IOException e) {
    					Log.e(TAG,"IOException in sendMessage " + e.getMessage());
    				}
    			}    			
    		}
    	});			    	
    }
    
    
}

// Separate thread for Listening
class ListenThread extends Thread {
	private Handler handler;
	private Handler callback;
	private InputStream in;
	private static final String TAG = ListenThread.class.getSimpleName();
	
	private boolean running;

	public ListenThread(Handler callback,InputStream in)
		throws IOException {
		this.callback = callback;
		this.in = in;
	}
	
	 @Override
	    public void run() {
	    	try{
	    		Looper.prepare();
	    		handler = new Handler();
	    		Looper.loop();
	    	} catch (Exception e) {
	    		Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
	    	}
	 }

	// Must be called after socket is already initialized.
		public synchronized void startListening() {						
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {				
						 Log.e(TAG, "Starting ...");
					        try {
					            while (in != null) {
					            	Log.d(TAG, "Waiting for incoming message");
					            	SVMPProtocol.Response m = SVMPProtocol.Response.parseDelimitedFrom(in);
					            	Log.d(TAG, "Received incoming message object of type " + m );

					                if(m != null){
					                    final Message message = Message.obtain(callback);//
					                    message.obj = m;
					                    message.sendToTarget();
					                }
					            }
					        } catch (Exception e) {
					        	running = false;
					        	Log.i(TAG, "Server connection disconnected.");
					        }							
					} finally {					
						//totalReceived++; 
					}				
				}
			});		
		}
		public synchronized void Stop() {
	    	handler.post(new Runnable() {
	    		@Override
	    		public void run() {
	    			try {
	    				Log.d(TAG, "Terminating connection");
	    				in.close();	    									
	    			} catch (Exception e) {
	    				Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
	    			}				
	    			Looper.myLooper().quit();
	    		}
	    	});
	    }

}

