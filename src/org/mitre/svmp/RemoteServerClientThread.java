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
public class RemoteServerClientThread extends Thread {

	private static final String TAG = RemoteServerClientThread.class.getSimpleName();

    private static boolean USE_SSL = true;
    // for testing, set true to disable server cert validity checking
    private static boolean SSL_DEBUG = false;

	private Handler send_handler;
	private String host;
	private int port;
	private Socket socket;
	
	// Receiver thread
	private ListenThread lthread = null;
	// UI thread's Handler that the receiver thread posts messages to
	private Handler callback;

	private boolean running;

	private OutputStream out = null;
	private InputStream in = null;

	private void socketConnect() throws UnknownHostException, IOException { 
		SocketFactory sf;

		Log.d(TAG, "Socket connecting to " + host + ":" + port);

		if (USE_SSL) {
			if (SSL_DEBUG)
				sf = SSLCertificateSocketFactory.getInsecure(0, null);
			else
				sf = SSLCertificateSocketFactory.getDefault(0, null);
		} else {
			sf = SocketFactory.getDefault();
		}
		socket = sf.createSocket(host, port);
		Log.d(TAG, "Socket connected to " + host + ":" + port);
		
		out = socket.getOutputStream();
		out.flush(); // not sure this is needed any more
		
		in = socket.getInputStream();
	}

	public RemoteServerClientThread(final Handler callback, final String host,
			final int port) throws IOException {
		this.host = host;
		this.port = port;
		this.callback = callback;
	}

	@Override
	public void run() {
		try {
			Looper.prepare();
			send_handler = new Handler();

			socketConnect();

			lthread = new ListenThread(callback, in);
			lthread.start();
			
			Looper.loop();
			running = true;
			
			Log.i(TAG, "Send thread exited cleanly");
		} catch (Throwable t) {
			Log.e(TAG, "Send Thread halted due to an error", t);
			lthread.Stop();
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
				} catch (Exception e) {
					Log.d(TAG, "Execption: " + Log.getStackTraceString(e));
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
		send_handler.post(new Runnable() {
			@Override
			public void run() {
				if (socket != null && socket.isConnected()) {
					try {
						msg.writeDelimitedTo(out);
					} catch (IOException e) {
						Log.e(TAG, "IOException in sendMessage " + e.getMessage());
					}
				}
			}
		});
	}
	
	public String getlocalIP() {
//		if (!socket.isConnected())
//			return null;
		return socket.getLocalAddress().getHostAddress();
	}

}

// Separate thread for Listening
class ListenThread extends Thread {
	private Handler callback;
	private InputStream in;
	private static final String TAG = ListenThread.class.getSimpleName();
	
	boolean running = true;

	public ListenThread(Handler callback, InputStream in) throws IOException {
		this.callback = callback;
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
