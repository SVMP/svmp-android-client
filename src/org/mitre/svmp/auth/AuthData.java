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
package org.mitre.svmp.auth;

import org.mitre.svmp.ConnectionInfo;
import org.mitre.svmp.protocol.SVMPProtocol.Authentication;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

import java.util.HashMap;

/**
 * @author Joe Portner
 * Stores authentication information to be sent to the proxy
 * If we have a session token, we first try to use it for authentication in lieu of prompting for input
 * If we do NOT have a session token (e.g. we have a password, security token, it is cleared after being accessed
 */
public final class AuthData {
	private static HashMap<Integer, AuthData> instances = new HashMap<Integer, AuthData>();

	private Request authRequest;
    private boolean hasSessionToken;

	// no public instantiations
	private AuthData() {}

    // used to add auth data (password, security token, etc)
	public static void setAuthRequest(ConnectionInfo connectionInfo, Request authRequest) {
        // if we have a session token, discard it
        AuthData instance = reset(connectionInfo.getConnectionID());

        // store this auth data
		instance.authRequest = authRequest;
	}

    public static Request getRequest(ConnectionInfo connectionInfo) {
        AuthData instance = getInstance(connectionInfo.getConnectionID());

        // get the auth data
        Request authRequest = instance.authRequest;

        // if we don't have a session token, stop storing the auth data (it's not a token, it's a password/etc)
        if (!hasSessionToken(connectionInfo))
            instance.authRequest = null;

        // return the auth data
        return authRequest;
    }

    // stores the session token and
    public static void setSessionToken(ConnectionInfo connectionInfo, String sessionToken) {
        if (connectionInfo != null && sessionToken != null) {
            AuthData instance = getInstance(connectionInfo.getConnectionID());

            // create an Authentication protobuf
            Authentication.Builder aBuilder = Authentication.newBuilder();
            // the full domain username is used (i.e. "domain\\username", or "username" if domain is blank)
            aBuilder.setUsername(connectionInfo.domainUsername());
            aBuilder.setSessionToken(sessionToken);

            // package the Authentication protobuf in a Request wrapper and store it
            Request.Builder rBuilder = Request.newBuilder();
            rBuilder.setType(Request.RequestType.USERAUTH);
            rBuilder.setAuthentication(aBuilder);
            instance.authRequest = rBuilder.build();
            instance.hasSessionToken = true;
        }
    }

    public static boolean hasSessionToken(ConnectionInfo connectionInfo) {
        AuthData instance = getInstance(connectionInfo.getConnectionID());
        return instance.hasSessionToken;
    }

    // create a new instance and return it (discarding an old instance if necessary)
    public static AuthData reset(int connectionID) {
        AuthData instance = new AuthData();
        instances.put(connectionID, instance);
        return instance;
    }

    // if an instance doesn't exist, create a new one; return the instance
    private static AuthData getInstance(int connectionID) {
        if (!instances.containsKey(connectionID))
            instances.put(connectionID, new AuthData());
        return instances.get(connectionID);
    }
}
