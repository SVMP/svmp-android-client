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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Looper;
import org.mitre.svmp.AuthData;
import org.mitre.svmp.Constants;
import org.mitre.svmp.RemoteServerClient;
import org.mitre.svmp.Utility;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderInfo;
import org.mitre.svmp.protocol.SVMPProtocol.LocationResponse;
import org.mitre.svmp.protocol.SVMPProtocol.LocationSubscribe;
import org.mitre.svmp.protocol.SVMPProtocol.LocationUnsubscribe;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.SensorType;

import android.content.Context;
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
public class ClientTestView extends TestEventView implements Constants {
    private static final String TAG = ClientTestView.class.getName();
    private float xScaleFactor, yScaleFactor = 0;
    private RemoteServerClient client;
    private ClientSideActivityDirect clientActivity;

    // track timestamp of the last update of each sensor we are tracking
    private long[] lastSensorUpdate = new long[SvmpSensors.MAX_SENSOR_TYPE+1];

    // minimum allowed time between sensor updates in nanoseconds
    private static final long MIN_SENSOR_INTERVAL = (1000 / 500) * 1000000; // 50Hz

    // Protocol connection states
    private int protocolState = PROTOCOLSTATE_UNAUTHENTICATED;

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
    public void startClient(ClientSideActivityDirect clientActivity, final String host, final int port, final int encryptionType) {
        this.clientActivity = clientActivity;
    	try {
        	protocolState = PROTOCOLSTATE_UNAUTHENTICATED;
            this.client = new RemoteServerClient(new MessageCallback(this), host, port, encryptionType);
            this.client.execute();
            sendAuthenticationMessage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeClient(){
        this.client.stop();
        protocolState = PROTOCOLSTATE_UNAUTHENTICATED;
    }

    public Boolean ClientRunning(){
    	if (this.client != null)
    		return this.client.status();
    	
    	return false;
    }
    
    public boolean onTouchEvent(final MotionEvent event) {
    	if (protocolState != PROTOCOLSTATE_PROXYREADY) return false;

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
		if (protocolState != PROTOCOLSTATE_PROXYREADY) return;

		int type = event.sensor.getType();

		if (event.timestamp < lastSensorUpdate[type] + MIN_SENSOR_INTERVAL)
			return;
		lastSensorUpdate[type] = event.timestamp;

		// assemble the message
		SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
		SVMPProtocol.SensorEvent.Builder e = SVMPProtocol.SensorEvent.newBuilder();
		msg.setType(RequestType.SENSOREVENT);
		e.setType(SensorType.valueOf(type));
		e.setAccuracy(event.accuracy);
		e.setTimestamp(event.timestamp);
		
		List<Float> vals = new ArrayList<Float>(event.values.length);
		for (float v : event.values) vals.add(v);
		e.addAllValues(vals);
		
		msg.setSensor(e);
		
		sendInputMessage(msg.build());
		//Log.d(TAG, "Sent sensor update for type " + SensorType.valueOf(type).name());
	}

    // called when a LocationListener triggers, converts the data and sends it to the VM
    public void onLocationChanged(Location location) {
		if (protocolState != PROTOCOLSTATE_PROXYREADY) return;

		SVMPProtocol.LocationUpdate locationUpdate = Utility.toLocationUpdate(location);
		SVMPProtocol.Request request = Utility.toRequest(locationUpdate);

		// send the Request to the VM
		sendInputMessage(request);
	}
	// called when a onProviderEnabled or onProviderDisabled triggers, converts the data and sends it to the VM
	public void onProviderEnabled(String s, boolean isEnabled) {
		if (protocolState != PROTOCOLSTATE_PROXYREADY) return;

		SVMPProtocol.LocationProviderEnabled providerEnabled = Utility.toLocationProviderEnabled(s, isEnabled);
		SVMPProtocol.Request request = Utility.toRequest(providerEnabled);

		// send the Request to the VM
		sendInputMessage(request);
	}
	// called when a onStatusChanged triggers, converts the data and sends it to the VM
	public void onStatusChanged(String s, int i, Bundle bundle) {
		if (protocolState != PROTOCOLSTATE_PROXYREADY) return;

		SVMPProtocol.LocationProviderStatus providerStatus = Utility.toLocationProviderStatus(s, i, bundle);
		SVMPProtocol.Request request = Utility.toRequest(providerStatus);

		// send the Request to the VM
		sendInputMessage(request);
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
    	
    	protocolState = PROTOCOLSTATE_AUTHENTICATING;
    }

    private void sendScreenInfoMessage() {
    	SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
    	msg.setType(RequestType.SCREENINFO);
    	
        sendInputMessage(msg.build());
        Log.d(TAG, "Sent screen info request");
        
        protocolState = PROTOCOLSTATE_GETSCREENINFO;
    }

    private void sendLocationProviderMessages() {
		LocationManager lm = (LocationManager) clientActivity.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

		// loop through all location providers
		List<String> providerNames = lm.getAllProviders();
		for(String providerName : providerNames){
            // skip the Passive provider
            if( !providerName.equals(LocationManager.PASSIVE_PROVIDER) ) {
                //get the provider information and package it into a Request
                LocationProvider provider = lm.getProvider(providerName);
                LocationProviderInfo providerInfo = Utility.toLocationProviderInfo(provider);
                Request request = Utility.toRequest(providerInfo);

                // send the Request to the VM
                sendInputMessage(request);
            }
		}
    }

    private void calcScaleFactor(final int toX, final int toY){
        this.xScaleFactor = (float)toX/(float)getWidth();
        this.yScaleFactor = (float)toY/(float)getHeight();
        this.yScaleFactor = (float)toY/(float)getHeight();
        Log.i(TAG, "Scalefactor: (" + xScaleFactor + "," + yScaleFactor + ")");
    }

    private boolean handleAuthenticationResponse(SVMPProtocol.Response msg) {
    	// check that we got an AUTHOK
    	switch (msg.getType()) {
    	case AUTHOK:
    		protocolState = PROTOCOLSTATE_VMREADYWAIT;
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

        // send the VM our location provider information
        sendLocationProviderMessages();

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
    	protocolState = PROTOCOLSTATE_PROXYREADY;

    	return true;
    }

    protected void handleLocationResponse(Response response) {
        LocationResponse locationResponse = response.getLocationResponse();
        // get the location manager
        LocationManager lm = (LocationManager) clientActivity.getSystemService(Context.LOCATION_SERVICE);

        // a response can either be to subscribe or to unsubscribe
        if( locationResponse.getType() == LocationResponse.LocationResponseType.SUBSCRIBE ) {
            LocationSubscribe locationSubscribe = locationResponse.getSubscribe();
            String provider = locationSubscribe.getProvider();
            Looper looper = Looper.myLooper(); // TODO: find out if we should use myLooper or mainLooper?

            // a subscribe request can either be one-time or long-term
            if( locationSubscribe.getType() == LocationSubscribe.LocationSubscribeType.SINGLE_UPDATE ) {
                LocationListener locationListener = clientActivity.getListenerSingle(provider);
                lm.requestSingleUpdate(
                        provider,
                        locationListener,
                        looper );
            }
            else if( locationSubscribe.getType() == LocationSubscribe.LocationSubscribeType.MULTIPLE_UPDATES ) {
                LocationListener locationListener = clientActivity.getListenerLongTerm(provider);
                lm.requestLocationUpdates(
                        provider,
                        locationSubscribe.getMinTime(),
                        locationSubscribe.getMinDistance(),
                        locationListener,
                        looper );
            }
        }
        else if( locationResponse.getType() == LocationResponse.LocationResponseType.UNSUBSCRIBE ) {
            LocationUnsubscribe locationUnsubscribe = locationResponse.getUnsubscribe();

            // unsubscribe from location updates for this provider
            // (we only get unsubscribe requests for long-term subscriptions)
            String provider = locationUnsubscribe.getProvider();
            clientActivity.removeLUpdates(provider);
        }
    }

    private static class MessageCallback extends Handler {
    	WeakReference<ClientTestView> ctvref;
    	
    	public MessageCallback(ClientTestView v) {
    		ctvref = new WeakReference<ClientTestView>(v);
    	}
    	
        @Override
        public void handleMessage(final Message msg) {
            ClientTestView ctv = ctvref.get();
            if( msg.arg1 > 0 ) { // error message
                Intent intent = new Intent();
                intent.putExtra("message", msg.arg1);
                ctv.clientActivity.setResult(Activity.RESULT_CANCELED, intent);
                ctv.clientActivity.finish();
            } else { // normal message
                SVMPProtocol.Response r = (SVMPProtocol.Response)msg.obj;
                switch (ctv.protocolState) {
                case PROTOCOLSTATE_UNAUTHENTICATED:
                    // we haven't sent anything to the server yet, shouldn't be anything to receive
                    return;
                case PROTOCOLSTATE_AUTHENTICATING:
                    ctv.handleAuthenticationResponse(r);
                    // expecting and AUTHOK or ERROR response
                case PROTOCOLSTATE_VMREADYWAIT:
                    // Got the authentication response, but have to wait for VMREADY before doing anything else
                    ctv.handleReadyResponse(r);
                case PROTOCOLSTATE_GETSCREENINFO:
                    // expecting a screen info response
                    ctv.handleScreenInfoResponse(r);
                case PROTOCOLSTATE_PROXYREADY:
                    if( r.getType() == ResponseType.LOCATION )
                        ctv.handleLocationResponse(r);
                    // done hand shaking, everything should be one-way to the server from here on
                    // (at least until we wire Intents and Notifications into this too)
                    return;
                }
            }
        }
    }

}
