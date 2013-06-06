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
package org.mitre.svmp.client;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.mitre.svmp.AuthData;
import org.mitre.svmp.RemoteServerClient;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.SensorType;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Client View.  Shows a circle to visualize the motion events sent to the server.  The Associated
 * Activity (clientsideactivity) call "startClient" to initiate message exchanges
 * @author Dave Bryson
 */
public class ClientTestView extends TestEventView  {
    private static final String TAG = "ClientTestView";
    private float xScaleFactor, yScaleFactor = 0;
    private RemoteServerClient client;
    private ClientSideActivityDirect clientActivity;

    //private long lastAccelUpdate = 0;
    //private long lastCompassUpdate = 0;
    private int sensorSize = -1;
    private long[] lastSensorUpdate;
    
    // minimum allowed time between sensor updates in nanoseconds
    private static final long MIN_SENSOR_INTERVAL = (1000 / 500) * 1000000; // 50Hz

    
    private static final int UNAUTHENTICATED = 0;
    private static final int AUTHENTICATING  = 1;
    private static final int VMREADYWAIT     = 2;
    private static final int GETSCREENINFO   = 3;
    private static final int PROXYREADY      = 4;
    private int protocolState = UNAUTHENTICATED;

    public void setSensorSize(int sensorSize) {
        this.sensorSize = sensorSize;
        lastSensorUpdate = new long[sensorSize];
    }

    public ClientTestView(Context context) {
        super(context);
    }

    public ClientTestView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public ClientTestView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }
    
    /**
     * Sends an initial ServerInfo message to discover the screen size of the remote emulator. Using this
     * info. it generates a "scaling factor" to offset pixel values between the client and server
     * @param clientActivity 
     * @param host
     * @param port
     */
    public void startClient(ClientSideActivityDirect clientActivity, final String host, final int port) {
        this.clientActivity = clientActivity;
    	try {
        	protocolState = UNAUTHENTICATED;
            this.client = new RemoteServerClient(new MessageCallback(this),host,port);
            this.client.execute();
            sendAuthenticationMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeClient(){
        this.client.stop();
        protocolState = UNAUTHENTICATED;
    }

    public Boolean ClientRunning(){
    	if (this.client != null)
    		return this.client.status();
    	
    	return false;
    }
    
    public boolean onTouchEvent(final MotionEvent event) {
    	if (protocolState != PROXYREADY) return false;

        // SEND REMOTE EVENT
    	SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
    	SVMPProtocol.TouchEvent.Builder eventmsg = SVMPProtocol.TouchEvent.newBuilder();
        SVMPProtocol.TouchEvent.PointerCoords.Builder p = SVMPProtocol.TouchEvent.PointerCoords.newBuilder();
    	    	
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_POINTER_DOWN:
            	eventmsg.setAction(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                break;
            case MotionEvent.ACTION_POINTER_UP:
            	eventmsg.setAction(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                break;
            default:
            	eventmsg.setAction(event.getActionMasked());
                break;
        }
        
        final int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            final float adjX = event.getX(i) * this.xScaleFactor;
            final float adjY = event.getY(i) * this.yScaleFactor;
            p.clear();
            p.setId(i);
            p.setX(adjX);
            p.setY(adjY);
            eventmsg.addItems(p.build());
        }

    	msg.setType(RequestType.TOUCHEVENT);
    	msg.setTouch(eventmsg);

        sendInputMessage(msg.build());

        return true;
    }
    
    public void onSensorEvent(SensorEvent event) {
		if (protocolState != PROXYREADY) return;

		SVMPProtocol.SensorType type = null;
		int sensorIndex = event.sensor.getType() - 1; // map sensor type to last sensor update array index

		if( lastSensorUpdate != null && lastSensorUpdate.length > sensorIndex ) {
			if (event.timestamp < lastSensorUpdate[sensorIndex] + MIN_SENSOR_INTERVAL) return;
				type = SensorType.valueOf(sensorIndex  + 1);
			lastSensorUpdate[sensorIndex] = event.timestamp;
		}
		else {
			return;
		}

		// assemble the message
		SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
		SVMPProtocol.SensorEvent.Builder e = SVMPProtocol.SensorEvent.newBuilder();
		msg.setType(RequestType.SENSOREVENT);
		e.setType(type);
		e.setAccuracy(event.accuracy);
		e.setTimestamp(event.timestamp);
		
		List<Float> vals = new ArrayList<Float>(event.values.length);
		for (float v : event.values) vals.add(v);
		e.addAllValues(vals);
		
		msg.setSensor(e);
		
		sendInputMessage(msg.build());
	}
    
    private void sendInputMessage(SVMPProtocol.Request message) {
    	if (client != null)
    		client.sendMessage(message);
    }
    
    private void sendAuthenticationMessage() {
    	SVMPProtocol.Request.Builder req = SVMPProtocol.Request.newBuilder();
    	SVMPProtocol.Authentication.Builder auth = SVMPProtocol.Authentication.newBuilder();
    	
    	req.setType(RequestType.USERAUTH);
    	auth.setUn(AuthData.getUsername());
    	auth.setPw(AuthData.getPassword());
    	req.setAuthentication(auth);
    	
    	sendInputMessage(req.build());
    	Log.d(TAG, "Sent authentication request");
    	
    	protocolState = AUTHENTICATING;
    }
    
    private void sendScreenInfoMessage() {
    	SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
    	msg.setType(RequestType.SCREENINFO);
    	
        sendInputMessage(msg.build());
        Log.d(TAG, "Sent screen info request");
        
        protocolState = GETSCREENINFO;
    }

    private void calcScaleFactor(final int toX, final int toY){
        this.xScaleFactor = (float)toX/(float)getWidth();
        this.yScaleFactor = (float)toY/(float)getHeight();
        Log.i(TAG, "Scalefactor: ("+xScaleFactor+","+yScaleFactor+")");
    }
    
    private boolean handleAuthenticationResponse(SVMPProtocol.Response msg) {
    	// check that we got an AUTHOK
    	switch (msg.getType()) {
    	case AUTHOK:
    		protocolState = VMREADYWAIT;
    		return true;
    	case ERROR:
    		// TODO: some kind of dialog popup
    		Log.e(TAG, "Authentication error: " + msg.getMessage());
    		return false;
    	default:
    		return false;
    	}
    }
    
    private boolean handleReadyResponse(SVMPProtocol.Response msg) {
    	// check that we got VMREADY
    	if (msg.getType() != ResponseType.VMREADY)
    		return false;
    	
    	// get RTSP URL for the video and start playback
    	clientActivity.initvideo(msg.getMessage());
    	
    	// if so, update state to VMREADY and send ScreenInfo Request message
    	sendScreenInfoMessage();

    	return true;
    }
        
    private boolean handleScreenInfoResponse(SVMPProtocol.Response msg) {
    	if (!msg.hasScreenInfo())
    		return false;
    	
    	final int x = msg.getScreenInfo().getX();
    	final int y = msg.getScreenInfo().getY();

    	Log.d(TAG, "Got the ServerInfo: xsize=" + x + " ; ysize=" + y);
    	calcScaleFactor(x,y);
    	Log.d(TAG, "Scale factor: " + xScaleFactor + " ; " + yScaleFactor);
    	protocolState = PROXYREADY;

    	return true;
    }
    
    private static class MessageCallback extends Handler {
    	WeakReference<ClientTestView> ctvref;
    	
    	public MessageCallback(ClientTestView v) {
    		ctvref = new WeakReference<ClientTestView>(v);
    	}
    	
        @Override
        public void handleMessage(final Message msg) {
        	SVMPProtocol.Response r = (SVMPProtocol.Response)msg.obj;
        	ClientTestView ctv = ctvref.get();
        	switch (ctv.protocolState) {
        	case UNAUTHENTICATED:
        		// we haven't sent anything to the server yet, shouldn't be anything to receive
        		return;
        	case AUTHENTICATING:
        		ctv.handleAuthenticationResponse(r);
        		// expecting and AUTHOK or ERROR response
        	case VMREADYWAIT:
        		// Got the authentication response, but have to wait for VMREADY before doing anything else
        		ctv.handleReadyResponse(r);
        	case GETSCREENINFO:
        		// expecting a screen info response
        		ctv.handleScreenInfoResponse(r);
        	case PROXYREADY:
        		// done hand shaking, everything should be one-way to the server from here on
        		// (at least until we wire Intents and Notifications into this too)
        		return;
        	}
        }
    }

}
