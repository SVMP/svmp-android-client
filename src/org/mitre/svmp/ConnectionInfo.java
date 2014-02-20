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
    private int authType;
    private String certificateAlias;

    // constructor
    public ConnectionInfo(int connectionID, String description, String username, String host, int port,
                          int encryptionType, int authType, String certificateAlias) {
        this.connectionID = connectionID;
        this.description = description;
        this.username = username;
        this.host = host;
        this.port = port;
        this.encryptionType = encryptionType;
        this.authType = authType;
        this.certificateAlias = certificateAlias;
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

    public int getAuthType() {
        return authType;
    }

    public String getCertificateAlias() {
        return certificateAlias;
    }

    // used to describe each ConnectionInfo in ConnectionList activity
    public String lineOneText() {
        return description;
    }

    public String lineTwoText() {
        return String.format("%s@%s:%d", username, host, port);
    }

}
