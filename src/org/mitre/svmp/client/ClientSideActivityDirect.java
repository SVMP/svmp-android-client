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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.List;
import java.util.Timer;

/**
 * @author Andy Pyles
 * Based on the original ClientSideActivity by Dave Bryson
 * Use MediaPlayer directly instead of VideoView provides more flexibility. 
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.client_side);

		// Get info passed to Intent
		Intent i = getIntent();
		host = i.getExtras().getString("host");
		port = i.getExtras().getInt("port");

		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
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

		List<Sensor> availableSensors = sm.getSensorList(Sensor.TYPE_ALL);
		for(Sensor currentSensor : availableSensors) {
			this.sm.unregisterListener(this,currentSensor);
		}
		view.setSensorSize(0);
		view.closeClient();
	}
	 
	private void init() {
		// only init if client is running;
		if (view.ClientRunning())
		    return;

		Log.i(TAG, "Client is not running. Connecting now to " + host + ":" + port);
		
		view.startClient(this, host, port);

		Log.d(TAG, "Starting sensors");
        //queryForSensors();
		initsensors();
	}

	private void initsensors() {
		Log.d(TAG, "startClient started registering listener");

		int maxTypeInt = -1;
		List<Sensor> availableSensors = sm.getSensorList(Sensor.TYPE_ALL);
		for(Sensor currentSensor : availableSensors) {
			this.sm.registerListener(this, currentSensor, SensorManager.SENSOR_DELAY_NORMAL);
			if(currentSensor.getType() > maxTypeInt)
				maxTypeInt = currentSensor.getType();
		}
		view.setSensorSize(maxTypeInt);
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
	
}
