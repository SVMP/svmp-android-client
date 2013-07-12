package org.mitre.svmp;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import org.mitre.svmp.protocol.SVMPProtocol;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;


public final class RemoteSendThread extends Thread {

	private static final String TAG = RemoteSendThread.class.getSimpleName();
	
	private Handler handler;
	
	private int totalQueued;
	
	private int totalCompleted;
	
	//private RemoteSendThreadListener listener;
	private String host;
	private int port;
	private boolean running;
	
	private Socket socket;
	private OutputStream out = null;
    private InputStream in = null;

	private RemoteListenThread _listenThread;
	
	// socket information..
	
    public RemoteSendThread(String host, int port) 	
    		throws IOException {
    	this.host = host;
    	this.port = port;    	
    	
    	Log.d(TAG, "Object Streams created");
    	this.running = false;
    }
    
    public String getlocalIP(){
    	/*if(!running)
    		return null;*/
    	
    	return socket.getLocalAddress().getHostAddress();
    }
	
	@Override
	public void run() {
		try {
			// preparing a looper on current thread			
			// the current thread is being detected implicitly
			Looper.prepare();
			handler = new Handler();
			
			SocketAddress sockaddr = new InetSocketAddress(this.host, this.port);
	    	socket = new Socket();
	    	try {
	    		Log.d(TAG, "Socket connecting to " + host + ":" + port);
				socket.connect(sockaddr, 5000); // 5 second timeout
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} //5 second connection timeout
	    	Log.d(TAG, "Socket connected to " + host + ":" + port);
	    	out = socket.getOutputStream();
	    	out.flush();
	    	in = socket.getInputStream();
			
	    	// startup listening thread  
			_listenThread.startListening(in); //
			
			Log.i(TAG, "DownloadThread entering the loop");

			// now, the handler will automatically bind to the
			// Looper that is attached to the current thread
			// You don't need to specify the Looper explicitly
			
			
			// After the following line the thread will start
			// running the message loop and will not normally
			// exit the loop unless a problem happens or you
			// quit() the looper (see below)
			Looper.loop();
			
			Log.i(TAG, "DownloadThread exiting gracefully");
		} catch (Throwable t) {
			Log.e(TAG, "DownloadThread halted due to an error", t);
		} 
	}
	
	// This method is allowed to be called from any thread
	public synchronized void requestStop() {
		// using the handler, post a Runnable that will quit()
		// the Looper attached to our DownloadThread
		// obviously, all previously queued tasks will be executed
		// before the loop gets the quit Runnable
		handler.post(new Runnable() {
			@Override
			public void run() {
				// This is guaranteed to run on the DownloadThread
				// so we can use myLooper() to get its looper
				Log.i(TAG, "DownloadThread loop quitting by request");
				// close network socket;
				try {
					Log.d(TAG, "Forcing Closing connection");
					in.close();
					out.close();
					socket.close();					
				} catch (Exception e) {
					Log.d(TAG,"Execption: " + Log.getStackTraceString(e));
				}				
				Looper.myLooper().quit();
			}
		});
	}
	
	
	// send passage within thread
	public synchronized void sendMessage(final SVMPProtocol.Request msg) {
		
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (socket != null && socket.isConnected()) {
		    		try {
						msg.writeDelimitedTo(out);
						//return true;
					} catch (IOException e) {
						Log.e(TAG,"IOException in sendMessage " + e.getMessage());
					}
		    	}
		    	//return false;
			}
			});			    	
    }
	
	public synchronized Boolean status() {
		return this.running;
	}
	public void addListenThread(RemoteListenThread listenThread) {
		// TODO Auto-generated method stub
		_listenThread = listenThread;		
		
	}


}