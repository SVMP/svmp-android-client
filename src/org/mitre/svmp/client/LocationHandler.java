/*
 * Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.svmp.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.mitre.svmp.AppRTCDemoActivity;
import org.mitre.svmp.Utility;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderInfo;
import org.mitre.svmp.protocol.SVMPProtocol.LocationResponse;
import org.mitre.svmp.protocol.SVMPProtocol.LocationSubscribe;
import org.mitre.svmp.protocol.SVMPProtocol.LocationUnsubscribe;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

public class LocationHandler implements LocationListener {
    private static final String TAG = LocationHandler.class.getName();
    
    private AppRTCDemoActivity activity;
    private LocationManager lm;

    // keeps track of what LocationListeners there are for a given LocationProvider
    private HashMap<String,SVMPLocationListener> locationListeners = new HashMap<String, SVMPLocationListener>();

    public LocationHandler(AppRTCDemoActivity activity) {
        this.activity = activity;
        lm = activity.getLocationManager();
    }
    
    public void removeLUpdates(String provider) {
        if( locationListeners.containsKey(provider) )
            lm.removeUpdates(locationListeners.get(provider));
    }
    
    private void initLocationUpdates() {
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
    
    // called when a LocationListener triggers, converts the data and sends it to the VM
    public void onLocationChanged(Location location) {

        SVMPProtocol.LocationUpdate locationUpdate = Utility.toLocationUpdate(location);
        SVMPProtocol.Request request = Utility.toRequest(locationUpdate);

        // send the Request to the VM
        activity.sendMessage(request);
    }
    
    // called when a onProviderEnabled or onProviderDisabled triggers, converts the data and sends it to the VM
    public void onProviderEnabled(String s, boolean isEnabled) {
        SVMPProtocol.LocationProviderEnabled providerEnabled = Utility.toLocationProviderEnabled(s, isEnabled);
        SVMPProtocol.Request request = Utility.toRequest(providerEnabled);

        // send the Request to the VM
        activity.sendMessage(request);
    }
    
    // called when a onStatusChanged triggers, converts the data and sends it to the VM
    public void onStatusChanged(String s, int i, Bundle bundle) {
        SVMPProtocol.LocationProviderStatus providerStatus = Utility.toLocationProviderStatus(s, i, bundle);
        SVMPProtocol.Request request = Utility.toRequest(providerStatus);

        // send the Request to the VM
        activity.sendMessage(request);
    }
    
    private void sendLocationProviderMessages() {
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
                activity.sendMessage(request);
            }
        }
    }
    
    public void handleLocationResponse(Response response) {
        LocationResponse locationResponse = response.getLocationResponse();

        // a response can either be to subscribe or to unsubscribe
        if( locationResponse.getType() == LocationResponse.LocationResponseType.SUBSCRIBE ) {
            LocationSubscribe locationSubscribe = locationResponse.getSubscribe();
            String provider = locationSubscribe.getProvider();
            Looper looper = Looper.myLooper(); // TODO: find out if we should use myLooper or mainLooper?

            // a subscribe request can either be one-time or long-term
            if( locationSubscribe.getType() == LocationSubscribe.LocationSubscribeType.SINGLE_UPDATE ) {
                LocationListener locationListener = getListenerSingle(provider);
                lm.requestSingleUpdate(
                        provider,
                        locationListener,
                        looper );
            }
            else if( locationSubscribe.getType() == LocationSubscribe.LocationSubscribeType.MULTIPLE_UPDATES ) {
                LocationListener locationListener = getListenerLongTerm(provider);
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
            removeLUpdates(provider);
        }
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
            onLocationChanged(location);

            // if this is a singleshot update, we don't need this listener anymore; remove it
            if( singleShot ) {
                lm.removeUpdates(this);
                locationListeners.remove(key);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.d(TAG, "onStatusChanged: Provider(" + s + ") Status(" + i + ")");
            LocationHandler.this.onStatusChanged(s, i, bundle);
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.d(TAG, "onProviderEnabled: Provider(" + s + ")");
            LocationHandler.this.onProviderEnabled(s, true);
        }

        @Override
        public void onProviderDisabled(String s) {
            Log.d(TAG, "onProviderDisabled: Provider(" + s + ")");
            LocationHandler.this.onProviderEnabled(s, false);
        }
    }



    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        
    }
}
