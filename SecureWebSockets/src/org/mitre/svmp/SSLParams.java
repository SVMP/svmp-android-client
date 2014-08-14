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

import javax.net.ssl.SSLContext;

/**
 * This stores SSL parameters to be used in various WebSocket files
 * @author Joe Portner
 */
public class SSLParams {
    private SSLContext sslContext;
    private String[] enabledCiphers;
    private String[] enabledProtocols;

    public SSLParams(SSLContext sslContext, String[] enabledCiphers, String[] enabledProtocols) {
        this.sslContext = sslContext;
        this.enabledCiphers = enabledCiphers;
        this.enabledProtocols = enabledProtocols;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }
}
