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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;

/**
 * Main remote view activity. 
 * 
 * Uses MediaPlayer directly for more flexibilyt versus VideoView as in earlier versions.
 * 
 * @author Dave Bryson
 * @author Andy Pyles
 * @author David Keppler
 * @author David Schoenheit
 */

public class ClientSideActivityDirect extends Activity implements SensorEventListener, SurfaceHolder.Callback, OnPreparedListener {

	protected static final String TAG = "ClientSideActivityDirect";
	private ClientTestView view;
	private String host;
	private int port;

	Timer dialogTimer = null;
	Dialog dialog = null;
	MediaPlayer player = null;

	private SensorManager sm;
	private SurfaceHolder sh=null;

	private LocationManager lm;
	
	private List<Sensor> registeredSensors = new ArrayList<Sensor>(SvmpSensors.MAX_SENSOR_TYPE);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.client_side);

		// Get info passed to Intent
		Intent i = getIntent();
		host = i.getExtras().getString("host");
		port = i.getExtras().getInt("port");

		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		//dialogTimer = new Timer();
    	view = (ClientTestView)findViewById(R.id.clientview);  
        view.bringToFront();
        view.requestFocus();
        view.requestFocusFromTouch();
        view.setClickable(true);

        sh=view.getHolder();
        sh.addCallback(this);

        sh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}
   
	private void cleanupvideo() {
	    	player.stop();
	        player.reset();
	        player.release();
	}
	 
	private void cleanupsensors() {
		 // Only cleanup if client is running
		if(!view.ClientRunning())
			 return;
		 
		Log.d(TAG,"cleanupsensors()");

		for(Sensor currentSensor : registeredSensors) {
			this.sm.unregisterListener(this,currentSensor);
		}
		view.closeClient();
	}

    private void cleanupLocationUpdates(){
        // loop through location listeners and remove subscriptions for each one
        Iterator it = locationListeners.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry pairs = (HashMap.Entry)it.next();
            lm.removeUpdates((LocationListener)pairs.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }

	private void init() {
		// only init if client is NOT running;
		if (view.ClientRunning())
		    return;

		Log.i(TAG, "Client is not running. Connecting now to " + host + ":" + port);
		
		view.startClient(this, host, port);

		Log.d(TAG, "Starting sensors");
		initsensors();
        initLocationUpdates();
	}

	private void initsensors() {
		Log.d(TAG, "startClient started registering listener");

		initSensor(SvmpSensors.TYPE_ACCELEROMETER);
		initSensor(SvmpSensors.TYPE_AMBIENT_TEMPERATURE);
		initSensor(SvmpSensors.TYPE_GRAVITY);
		initSensor(SvmpSensors.TYPE_GYROSCOPE);
		initSensor(SvmpSensors.TYPE_LIGHT);
		initSensor(SvmpSensors.TYPE_LINEAR_ACCELERATION);
		initSensor(SvmpSensors.TYPE_MAGNETIC_FIELD);
		initSensor(SvmpSensors.TYPE_ORIENTATION);
		initSensor(SvmpSensors.TYPE_PRESSURE);
		initSensor(SvmpSensors.TYPE_PROXIMITY);
		initSensor(SvmpSensors.TYPE_RELATIVE_HUMIDITY);
		initSensor(SvmpSensors.TYPE_ROTATION_VECTOR);
		initSensor(SvmpSensors.TYPE_TEMPERATURE);
		
		// Virtual sensors created from inputs of others
		//   TYPE_GRAVITY
		//   TYPE_LINEAR_ACCELERATION
		//   TYPE_ORIENTATION
		//   TYPE_ROTATION_VECTOR
	}
	
	private boolean initSensor(int type) {
		Sensor s = sm.getDefaultSensor(type);
		if (s != null) {
			Log.i(TAG, "Registering for sensor: (type " + s.getType() + ") " + s.getVendor() + " " + s.getName());
			sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
			registeredSensors.add(s);
			return true;
		} else {
			Log.e(TAG, "Failed registering listener for default sensor of type " + type);
			return false;
		}
	}

    private void initLocationUpdates() {
    }

	public void initvideo(String url) {
		Log.d(TAG,"initvideo()");

		Log.i(TAG, "Starting video from: " + url);
		try {
			player.setDataSource(url);
			player.prepareAsync();

		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	 
    public void onPrepared(MediaPlayer mediaplayer) {
        Log.d(TAG,"onPrepared()");

        mediaplayer.start();
        Log.d(TAG,"done with mediplayer.start()");
    }
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG,"surfaceCreated()");
		
		// connect to the server and other startup
        init();
        
		try {
			player = new MediaPlayer();

			player.setDisplay(holder);
			player.setOnPreparedListener(this);
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    	Log.d(TAG,"surfaceDestroyed()");
    	cleanupvideo();
    	cleanupsensors();
        cleanupLocationUpdates();
    }

    @Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		view.onSensorEvent(event);
	}

    // keeps track of what LocationListeners there are for a given LocationProvider
    private HashMap<String,SVMPLocationListener> locationListeners = new HashMap<String, SVMPLocationListener>();

    protected SVMPLocationListener getListenerSingle(String provider) {
        // generate a unique name for this key (each single subscription is disposed after receiving one update)
        String uniqueName = provider + String.format("%.3f",  System.currentTimeMillis() / 1000.0);

        // add a listener for this key
        locationListeners.put( uniqueName, new SVMPLocationListener(uniqueName, true) );

        return locationListeners.get(uniqueName);
    }

    protected SVMPLocationListener getListenerLongTerm(String provider) {
        // if the HashMap doesn't contain a listener for this key, add one
        if( !locationListeners.containsKey(provider) )
            locationListeners.put( provider, new SVMPLocationListener(provider, false) );

        return locationListeners.get(provider);
    }

    class SVMPLocationListener implements LocationListener {

        private String key;
        private boolean singleShot;

        public SVMPLocationListener(String key, boolean singleShot) {
            this.key = key;
            this.singleShot = singleShot;
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged: Provider(" + location.getProvider() + "), singleShot(" + singleShot + "), " + location.toString());
            view.onLocationChanged(location);

            // if this is a singleshot update, we don't need this listener anymore; remove it
            if( singleShot ) {
                lm.removeUpdates(this);
                locationListeners.remove(key);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.d(TAG, "onStatusChanged: Provider(" + s + ") Status(" + i + ")");
            view.onStatusChanged(s, i, bundle);
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.d(TAG, "onProviderEnabled: Provider(" + s + ")");
            view.onProviderEnabled(s, true);
        }

        @Override
        public void onProviderDisabled(String s) {
            Log.d(TAG, "onProviderDisabled: Provider(" + s + ")");
            view.onProviderEnabled(s, false);
        }
    }

    public void removeLUpdates(String provider) {
        if( locationListeners.containsKey(provider) )
            lm.removeUpdates(locationListeners.get(provider));
    }
}
