/*
 Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.

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

import android.content.*;
import android.preference.PreferenceManager;
import org.mitre.svmp.Constants;
import org.mitre.svmp.Utility;
import org.webrtc.videoengine.ViERenderer;
import org.webrtc.videoengineapp.IViEAndroidCallback;
import org.webrtc.videoengineapp.ViEAndroidJavaAPI;

import android.app.Activity;
import android.app.Dialog;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;

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

public class ClientSideActivityDirect extends Activity implements 
SensorEventListener, SurfaceHolder.Callback, IViEAndroidCallback, Constants {

	protected static final String TAG = ClientSideActivityDirect.class.getName();
	private ClientTestView view;
	private String host;
	private int port;
    private int encryptionType;
	private String ip;
	private String VM;

	Timer dialogTimer = null;
	Dialog dialog = null;
//	MediaPlayer player = null;

	private SensorManager sm;
	private SurfaceHolder sh=null;

	private LocationManager lm;
	
	private List<Sensor> registeredSensors = new ArrayList<Sensor>(PREFERENCES_SENSORS_KEYS.length);
	
	private boolean videostarted=false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.client_side);
		Intent i = getIntent();

		// Full Wakelock unnecessary, this is the new recommended method.
		Window w = getWindow(); // in Activity's onCreate() for instance
		w.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			

		// Ensure WiFi doesn't go into sleep mode
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null)
		{
			WifiLock wifiLock = null;
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL , "MyWifiLock");
			wifiLock.acquire();
		}
		
		
		// Get info passed to Intent
		host = i.getExtras().getString("host");
		port = i.getExtras().getInt("port");
        encryptionType = i.getExtras().getInt("encryptionType");
		ip = i.getExtras().getString("ip");
		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		//dialogTimer = new Timer();
		
		System.out.println();
		
		view = (ClientTestView)findViewById(R.id.clientview1);  
		view.bringToFront();
        view.requestFocus();
        view.requestFocusFromTouch();
        view.setClickable(true);

        sh=view.getHolder();
        sh.addCallback(this);

        sh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
	}
   
	private void cleanupvideo() {
		//player.stop();
		//player.reset();
		//player.release();
		 Log.d(TAG, "stopAll");

	        if (vieAndroidAPI != null) {
	          
	        	stopVoiceEngine();
	          

	           // if (videostarted) {
	                videostarted = false;
	                vieAndroidAPI.StopRender(channel);
	                vieAndroidAPI.StopReceive(channel);
	                vieAndroidAPI.StopSend(channel);
	                vieAndroidAPI.RemoveRemoteRenderer(channel);
	                vieAndroidAPI.ViE_DeleteChannel(channel);
	                channel = -1;
	                //vieAndroidAPI.StopCamera(cameraId);
	                vieAndroidAPI.Terminate();
	                mLlRemoteSurface.removeView(remoteSurfaceView);
	                //mLlLocalSurface.removeView(svLocal);
	                remoteSurfaceView = null;                
	           // }
	            //cleanupsensors();
	            //cleanupLocationUpdates();
	        }
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
    
    /* stop video, sensors and location updates. Occurs on onStop() events*/
    private void cleanupAll() {
    	cleanupLocationUpdates();
    	cleanupsensors();
    	cleanupvideo();
    }

	private void init() {
		// only init if client is NOT running;
		if (view.ClientRunning())
		    return;

		Log.i(TAG, "Client is not running. Connecting now to " + host + ":" + port);
		
		view.startClient(this, host, port, encryptionType);

		Log.d(TAG, "Starting sensors");
		initsensors();
		initLocationUpdates();
		//startCall();
	}

	private void initsensors() {
		Log.d(TAG, "startClient started registering listener");

        // get preferences to determine which sensors we should listen to
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // loop through preferences...
        for( int i = 0; i < PREFERENCES_SENSORS_KEYS.length; i++ ) {
            // if this sensor is enabled in the preferences, register a listener for it
            if( Utility.getPrefBool(this, PREFERENCES_SENSORS_KEYS[i], PREFERENCES_SENSORS_DEFAULTVALUES[i]) )
                initSensor(i+1); // sensors start at 1, not 0
        }
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

	public void initvideo(String ipaddr) {
		Log.d(TAG,"initvideo()");

		VM = ipaddr;
		startCall();


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
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    	Log.d(TAG,"surfaceDestroyed()");
//    	cleanupvideo();
//    	cleanupsensors();
//      cleanupLocationUpdates();
//   	cleanupAll();
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
	
	
	//BELOW IS THE WEBRTC CODE	
	private static final int VID_PORT = 6000;
	
	
	private ViEAndroidJavaAPI vieAndroidAPI = null;

    // remote renderer
    public SurfaceView remoteSurfaceView = null;

    // channel number
    private int channel = -1;
    private int voiceChannel = -1;


    // Constant
    private static final int RECEIVE_CODEC_FRAMERATE = 15;
    private static final int INIT_BITRATE = 500;
    // Zero means don't automatically start/stop calls.
    
     
    private LinearLayout mLlRemoteSurface = null;
    private boolean enableVideoReceive = true;
    private boolean enableVideo = true;
    private CheckBox cbStats;
    public enum RenderType {
        OPENGL,
        SURFACE,
        MEDIACODEC
    }
    RenderType renderType = RenderType.SURFACE;

    // Video settings
    private int codecType = 0;
    private int codecSizeWidth = 0;
    private int codecSizeHeight = 0;
    private int receivePortVideo = 6000;
    private boolean enableNack = true;

    int currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    int currentCameraOrientation = 0;
    
 // Stats variables
    private int frameRateI;
    private int bitRateI;
    private int packetLoss;
    private int frameRateO;
    private int bitRateO;
    private int numCalls = 0;

    private int widthI;
    private int heightI;

   
	public void startCall() {
        int ret = 0;
        

        mLlRemoteSurface = (LinearLayout) findViewById(R.id.testView);
        
        if (null == vieAndroidAPI) {
            vieAndroidAPI = new ViEAndroidJavaAPI(this);
        }
        
        
        
        setupVoE();                
        vieAndroidAPI.GetVideoEngine();
        vieAndroidAPI.Init(true);

        
        
        codecType = 0;
        int voiceCodecType = 0;

        String sCodecSize = "320x240";
        codecSizeWidth = 320;
        codecSizeHeight = 240;

        boolean loopbackMode = false;
        boolean enableVoice = true;
        boolean enableVideoSend = true;
        enableVideoReceive = true;
        enableVideo = true;

        int destinationPortVideo = 6000;
        int receivePortVideo = 6000;

        enableNack  = false;
        boolean enableAGC = false;
        boolean enableAECM = false;
        boolean enableNS = false;
        
        SurfaceView svLocal = ViERenderer.CreateLocalRenderer(this);


        Log.d(TAG,"!!!setting SEND destination to "+VM);
        channel = vieAndroidAPI.CreateChannel(voiceChannel);        
        ret = vieAndroidAPI.SetLocalReceiver(channel,
        		VID_PORT);
        ret = vieAndroidAPI.SetSendDestination(channel,
        		destinationPortVideo,
        		VM);


        Log.v(TAG, "Create SurfaceView Render");
        remoteSurfaceView = ViERenderer.CreateRenderer(this, false);



        if (mLlRemoteSurface != null) {
        	mLlRemoteSurface.addView(remoteSurfaceView);
        	System.out.println("Added view");
        }

        ret = vieAndroidAPI.AddRemoteRenderer(channel, remoteSurfaceView);


        ret = vieAndroidAPI.SetReceiveCodec(channel,
        		codecType,
        		INIT_BITRATE,
        		codecSizeWidth,
        		codecSizeHeight,
        		RECEIVE_CODEC_FRAMERATE);
        ret = vieAndroidAPI.StartRender(channel);
        ret = vieAndroidAPI.StartReceive(channel);
                

        // TODO(leozwang): Add more options besides PLI, currently use pli
        // as the default. Also check return value.
        
        // TODO(apyles): Allow this to be configurable for now just support PLI
        ret = vieAndroidAPI.EnablePLI(channel, true);        
        //ret = vieAndroidAPI.EnableNACK(channel, enableNack);
        ret = vieAndroidAPI.SetCallback(channel, this);
        System.out.println("Started Call");
        videostarted=true;

    }

	
	
	private void stopVoiceEngine() {
        // Stop send
        if (0 != vieAndroidAPI.VoE_StopSend(voiceChannel)) {
            Log.d(TAG, "VoE stop send failed");
        }

        // Stop listen
        if (0 != vieAndroidAPI.VoE_StopListen(voiceChannel)) {
            Log.d(TAG, "VoE stop listen failed");
        }

        // Stop playout
        if (0 != vieAndroidAPI.VoE_StopPlayout(voiceChannel)) {
            Log.d(TAG, "VoE stop playout failed");
        }

        if (0 != vieAndroidAPI.VoE_DeleteChannel(voiceChannel)) {
            Log.d(TAG, "VoE delete channel failed");
        }
        voiceChannel = -1;

        // Terminate
        if (0 != vieAndroidAPI.VoE_Terminate()) {
            Log.d(TAG, "VoE terminate failed");
        }
    }
	
    private boolean enableTrace = true;
    private int receivePortVoice = 11113;
    private int destinationPortVoice = 11113;

    private int setupVoE() {
        // Create VoiceEngine
        // Error logging is done in native API wrapper
        vieAndroidAPI.VoE_Create(getApplicationContext());

        // Initialize
        if (0 != vieAndroidAPI.VoE_Init(enableTrace)) {
            Log.d(TAG, "VoE init failed");
            return -1;
        }

        // Suggest to use the voice call audio stream for hardware volume controls
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        return 0;
    }
    
   
   

    private int startVoiceEngine(String ipAddress) {
        // Create channel
        voiceChannel = vieAndroidAPI.VoE_CreateChannel();
        if (0 > voiceChannel) {
            Log.d(TAG, "VoE create channel failed");
            return -1;
        }

        // Set local receiver
        if (0 != vieAndroidAPI.VoE_SetLocalReceiver(voiceChannel,
                        receivePortVoice)) {
            Log.d(TAG, "VoE set local receiver failed");
        }

        if (0 != vieAndroidAPI.VoE_StartListen(voiceChannel)) {
            Log.d(TAG, "VoE start listen failed");
        }

        // Route audio
        routeAudio(true);

        // set volume to default value
        if (0 != vieAndroidAPI.VoE_SetSpeakerVolume(204)) {
            Log.d(TAG, "VoE set speaker volume failed");
        }

        // Start playout
        if (0 != vieAndroidAPI.VoE_StartPlayout(voiceChannel)) {
            Log.d(TAG, "VoE start playout failed");
        }

        if (0 != vieAndroidAPI.VoE_SetSendDestination(voiceChannel,
                                                      destinationPortVoice,
                                                      ipAddress)) {
            Log.d(TAG, "VoE set send  destination failed");
        }

        if (0 != vieAndroidAPI.VoE_SetSendCodec(voiceChannel, 0)) {
            Log.d(TAG, "VoE set send codec failed");
        }

        if (0 != vieAndroidAPI.VoE_SetECStatus(false)) {
            Log.d(TAG, "VoE set EC Status failed");
        }

        if (0 != vieAndroidAPI.VoE_SetAGCStatus(false)) {
            Log.d(TAG, "VoE set AGC Status failed");
        }

        if (0 != vieAndroidAPI.VoE_SetNSStatus(false)) {
            Log.d(TAG, "VoE set NS Status failed");
        }

        if (0 != vieAndroidAPI.VoE_StartSend(voiceChannel)) {
            Log.d(TAG, "VoE start send failed");
        }

        return 0;
    }

    private void routeAudio(boolean enableSpeaker) {
        if (0 != vieAndroidAPI.VoE_SetLoudspeakerStatus(enableSpeaker)) {
            Log.d(TAG, "VoE set louspeaker status failed");
        }
    }

	public int updateStats(int inFrameRateI, int inBitRateI,
            int inPacketLoss, int inFrameRateO, int inBitRateO) {
        frameRateI = inFrameRateI;
        bitRateI = inBitRateI;
        packetLoss = inPacketLoss;
        frameRateO = inFrameRateO;
        bitRateO = inBitRateO;
        return 0;
    }

    public int newIncomingResolution(int width, int height) {
	widthI = width;
	heightI = height;
	return 0;
    }

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}


	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onStart() {		
		super.onStart();
		//init(); 
//		
//		if(!videostarted){
//			//startvideo			
//		}
	}

	/* callback when video command has been sent */
	public void videoIsStopped() {
			videostarted=false;
			/* callback when video command has been sent */
			cleanupAll();
			
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		Log.d(TAG,"onStop()");
		view.sendVideoStopRequest();
		/*if(videostarted){
			// async send video stop request. videoIsStopped() is then called
			
		}*/	
		
	}
	
	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
	}
    
    
    
}
