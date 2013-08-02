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
public class ConnectionInfo {
    private int ID;
    private String description;
    private String username;
    private String host;
    private int port;
    private int encryptionType;

    // overload constructor for a new ConnectionInfo that does not have an ID yet
    public ConnectionInfo(String description, String username, String host, int port, int encryptionType) {
        this(0, description, username, host, port, encryptionType);
    }

    // constructor
    public ConnectionInfo(int ID, String description, String username, String host, int port, int encryptionType) {
        this.ID = ID;
        this.description = description;
        this.username = username;
        this.host = host;
        this.port = port;
        this.encryptionType = encryptionType;
    }

    // getters
    public int getID() {
        return ID;
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

    // used to describe each ConnectionInfo in ConnectionList activity
    public String toString() {
        return description;
    }

    public String toString2() {
        return username + "@" + host + ":" + port;
    }
}
