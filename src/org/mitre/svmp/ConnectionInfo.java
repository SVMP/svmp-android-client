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

/**
 * @author Joe Portner
 */
public class ConnectionInfo implements Constants{
    private int connectionID;
    private String description;
    private String username;
    private String host;
    private int port;
    private int encryptionType;
    private String domain;
    private int authType;

    // overload constructor for a new ConnectionInfo that does not have a ConnectionID yet
    public ConnectionInfo(String description, String username, String host, int port, int encryptionType, String domain, int authType) {
        this(0, description, username, host, port, encryptionType, domain, authType);
    }

    // constructor
    public ConnectionInfo(int connectionID, String description, String username, String host, int port, int encryptionType, String domain, int authType) {
        this.connectionID = connectionID;
        this.description = description;
        this.username = username;
        this.host = host;
        this.port = port;
        this.encryptionType = encryptionType;
        this.domain = domain;
        this.authType = authType;
    }

    // getters
    public int getConnectionID() {
        return connectionID;
    }

    public String getDescription() {
        return description;
    }

    public String getUsername() {
        return username;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getEncryptionType() {
        return encryptionType;
    }

    public String getDomain() {
        return domain;
    }

    public int getAuthType() {
        return authType;
    }

    // used to describe each ConnectionInfo in ConnectionList activity
    public String lineOneText() {
        return description;
    }

    public String lineTwoText() {
        return String.format("%s@%s:%d", username, host, port);
    }

    // used to show the username (and optionally, the domain) in the authentication dialog
    public String domainUsername() {
        if (domain.length() > 0)
            return String.format("%s\\%s", domain, username);
        return username;
    }
}
