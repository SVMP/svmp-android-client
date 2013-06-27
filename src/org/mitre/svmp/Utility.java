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

import android.location.Location;
import android.location.LocationProvider;
import android.os.Bundle;
import org.mitre.svmp.protocol.SVMPProtocol.LocationRequest;
import org.mitre.svmp.protocol.SVMPProtocol.LocationUpdate;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderInfo;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderEnabled;
import org.mitre.svmp.protocol.SVMPProtocol.LocationProviderStatus;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

/**
 * @author David Schoenheit, Joe Portner
 */
public class Utility {
	public static LocationProviderInfo toLocationProviderInfo(LocationProvider provider) {
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

		// build the LocationProviderInfo
		return lpiBuilder.build();
	}

	public static LocationProviderStatus toLocationProviderStatus(String s, int i, Bundle bundle) {
		// create a LocationProviderStatus Builder
		LocationProviderStatus.Builder lpsBuilder = LocationProviderStatus.newBuilder()
                // set required variables
				.setProvider(s)
				.setStatus(i);

		//TODO: add bundle if it is not null

		// build the LocationProviderStatus
		return lpsBuilder.build();
	}

	public static LocationProviderEnabled toLocationProviderEnabled(String s, boolean isEnabled) {
		// create a LocationProviderEnabled Builder
		LocationProviderEnabled.Builder lpeBuilder = LocationProviderEnabled.newBuilder()
                // set required variables
				.setProvider(s)
				.setEnabled(isEnabled);

		// build the LocationProviderEnabled
		return lpeBuilder.build();
	}

    public static LocationUpdate toLocationUpdate(Location location) {
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

        // build the LocationUpdate
        return luBuilder.build();
    }

    // overload
	public static Request toRequest(LocationProviderInfo providerInfo) {
		// pack LocationProviderInfo into LocationRequest wrapper
		LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
				.setType(LocationRequest.LocationRequestType.PROVIDERINFO)
				.setProviderInfo(providerInfo);

        // build the Request
        return toRequest(lrBuilder);
	}

    // overload
	public static Request toRequest(LocationProviderEnabled providerEnabled) {
		// pack LocationProviderEnabled into LocationRequest wrapper
		LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
				.setType(LocationRequest.LocationRequestType.PROVIDERENABLED)
				.setProviderEnabled(providerEnabled);

        // build the Request
        return toRequest(lrBuilder);
	}

    // overload
	public static Request toRequest(LocationProviderStatus providerStatus) {
		// pack LocationProviderStatus into LocationRequest wrapper
		LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
				.setType(LocationRequest.LocationRequestType.PROVIDERSTATUS)
				.setProviderStatus(providerStatus);

        // build the Request
        return toRequest(lrBuilder);
	}

    // overload
    public static Request toRequest(LocationUpdate locationUpdate) {
        // pack LocationUpdate into LocationRequest wrapper
        LocationRequest.Builder lrBuilder = LocationRequest.newBuilder()
                .setType(LocationRequest.LocationRequestType.LOCATIONUPDATE)
                .setUpdate(locationUpdate);

        // build the Request
        return toRequest(lrBuilder);
    }

	public static Request toRequest(LocationRequest.Builder lrBuilder) {
		// pack LocationRequest into Request wrapper
		Request.Builder rBuilder = Request.newBuilder()
				.setType(Request.RequestType.LOCATION)
				.setLocationRequest(lrBuilder);

		// build the Request
		return rBuilder.build();
	}
}
