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
package org.mitre.svmp;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationProvider;
import android.os.Bundle;
import android.preference.PreferenceManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.protocol.SVMPProtocol.LocationRequest;
import org.mitre.svmp.protocol.SVMPProtocol.LocationUpdate;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderInfo;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderEnabled;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderStatus;
import org.mitre.svmp.protocol.SVMPProtocol.RotationInfo;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

/**
 * @author David Schoenheit, Joe Portner
 */
public class Utility {
    // transforms a CellNetwork integer to an appropriate string (see TelephonyManager)
    public static String cellNetwork(int cellNetwork) {
        String network;
        switch(cellNetwork) {
            // use literals in cases for backwards compatibility (past versions of Android don't have later networks)
            case 1:
                network = "GPRS";
                break;
            case 2:
                network = "EDGE";
                break;
            case 3:
                network = "UMTS";
                break;
            case 4:
                network = "CDMA";
                break;
            case 5:
                network = "EVDO_0";
                break;
            case 6:
                network = "EVDO_A";
                break;
            case 7:
                network = "1xRTT";
                break;
            case 8:
                network = "HSDPA";
                break;
            case 9:
                network = "HSUPA";
                break;
            case 10:
                network = "HSPA";
                break;
            case 11:
                network = "IDEN";
                break;
            case 12:
                network = "EVDO_B";
                break;
            case 13:
                network = "LTE";
                break;
            case 14:
                network = "EHRPD";
                break;
            case 15:
                network = "HSPAP";
                break;
            case 0:
            default:
                network = "UNKNOWN";
                break;
        }
        return network;
    }

    public static String getPrefString(Context context, int keyId, int defaultValueId) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = context.getString(keyId);
        String defaultValue = context.getString(defaultValueId);

        return sharedPreferences.getString(key, defaultValue);
    }

    public static int getPrefInt(Context context, int keyId, int defaultValueId) {
        String prefString = getPrefString(context, keyId, defaultValueId);

        int value = 0;
        try {
            value = Integer.parseInt(prefString);
        } catch( Exception e ) { /* don't care */ }

        return value;
    }

    public static boolean getPrefBool(Context context, int keyId, int defaultValueId) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = context.getString(keyId);
        boolean defaultValue = false;
        try {
            defaultValue = Boolean.parseBoolean(context.getString(defaultValueId));
        } catch( Exception e ) { /* don't care */ }

        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public static Request toRequest_LocationProviderInfo(LocationProvider provider) {
        // create a LocationProviderInfo Builder
        LocationProviderInfo.Builder lpiBuilder = LocationProviderInfo.newBuilder()
                // set required variables
                .setProvider(provider.getName())
                .setRequiresNetwork(provider.requiresNetwork())
                .setRequiresSatellite(provider.requiresSatellite())
                .setRequiresCell(provider.requiresCell())
                .setHasMonetaryCost(provider.hasMonetaryCost())
                .setSupportsAltitude(provider.supportsAltitude())
                .setSupportsSpeed(provider.supportsSpeed())
                .setSupportsBearing(provider.supportsBearing())
                .setPowerRequirement(provider.getPowerRequirement())
                .setAccuracy(provider.getAccuracy());

        // pack LocationProviderInfo into LocationRequest wrapper
        LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
                .setType(LocationRequest.LocationRequestType.PROVIDERINFO)
                .setProviderInfo(lpiBuilder);

        // build the Request
        return toRequest_LocationRequest(lrBuilder);
    }

    public static Request toRequest_LocationProviderStatus(String s, int i, Bundle bundle) {
        // create a LocationProviderStatus Builder
        LocationProviderStatus.Builder lpsBuilder = LocationProviderStatus.newBuilder()
                // set required variables
                .setProvider(s)
                .setStatus(i);

        // pack LocationProviderStatus into LocationRequest wrapper
        LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
                .setType(LocationRequest.LocationRequestType.PROVIDERSTATUS)
                .setProviderStatus(lpsBuilder);

        // build the Request
        return toRequest_LocationRequest(lrBuilder);
    }

    public static Request toRequest_LocationProviderEnabled(String s, boolean isEnabled) {
        // create a LocationProviderEnabled Builder
        LocationProviderEnabled.Builder lpeBuilder = LocationProviderEnabled.newBuilder()
                // set required variables
                .setProvider(s)
                .setEnabled(isEnabled);

        // pack LocationProviderEnabled into LocationRequest wrapper
        LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
                .setType(LocationRequest.LocationRequestType.PROVIDERENABLED)
                .setProviderEnabled(lpeBuilder);

        // build the Request
        return toRequest_LocationRequest(lrBuilder);
    }

    public static Request toRequest_LocationUpdate(Location location) {
        // create a LocationUpdate Builder
        LocationUpdate.Builder luBuilder = LocationUpdate.newBuilder()
                // set required variables
                .setLatitude(location.getLatitude())
                .setLongitude(location.getLongitude())
                .setTime(location.getTime())
                .setProvider(location.getProvider());

        // set optional variables
        if( location.hasAccuracy() )
            luBuilder.setAccuracy(location.getAccuracy());
        if( location.hasAltitude() )
            luBuilder.setAltitude(location.getAltitude());
        if( location.hasBearing() )
            luBuilder.setBearing(location.getBearing());
        if( location.hasSpeed() )
            luBuilder.setSpeed(location.getSpeed());

        // pack LocationUpdate into LocationRequest wrapper
        LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
                .setType(LocationRequest.LocationRequestType.LOCATIONUPDATE)
                .setUpdate(luBuilder);

        // build the Request
        return toRequest_LocationRequest(lrBuilder);
    }

    public static Request toRequest_LocationRequest(LocationRequest.Builder lrBuilder) {
        // pack LocationRequest into Request wrapper
        Request.Builder rBuilder = Request.newBuilder()
                .setType(Request.RequestType.LOCATION)
                .setLocationRequest(lrBuilder);

        // build the Request
        return rBuilder.build();
    }

    public static Request toRequest_RotationInfo(int rotation) {
        // create a RotationInfo Builder
        RotationInfo.Builder riBuilder = RotationInfo.newBuilder()
                // set required variables
                .setRotation(rotation);


        // pack RotationInfo into Request wrapper
        Request.Builder rBuilder = Request.newBuilder()
                .setType(Request.RequestType.ROTATION_INFO)
                .setRotationInfo(riBuilder);

        // build the Request
        return rBuilder.build();
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
