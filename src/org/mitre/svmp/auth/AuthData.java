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

import org.json.JSONObject;
import org.mitre.svmp.common.ConnectionInfo;

import java.util.HashMap;

/**
 * @author Joe Portner
 * Stores authentication information to be sent to the proxy
 * If we have a session token, we first try to use it for authentication in lieu of prompting for input
 * If we do NOT have a session token (e.g. we have a password, security token, it is cleared after being accessed
 */
public final class AuthData {
    // maps ConnectionID to Request objects that contain auth info (password, etc)
    private static HashMap<Integer, JSONObject> authDataMap = new HashMap<Integer, JSONObject>();

    // no public instantiations
    private AuthData() {}

    // used to add auth data (password, security token, etc)
    public static void setAuthJSON(ConnectionInfo connectionInfo, JSONObject jsonObject) {
        // store this auth data
        authDataMap.put(connectionInfo.getConnectionID(), jsonObject);
    }

    public static JSONObject getJSON(ConnectionInfo connectionInfo) {
        // get the JSON and remove it from the map (returns null value if it doesn't exist)
        return authDataMap.remove(connectionInfo.getConnectionID());
    }
}
