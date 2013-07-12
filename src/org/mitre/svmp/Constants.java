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
public interface Constants {
    public static final int DEFAULT_PORT = 8002;
    public static final int DEFAULT_ENCRYPTION_TYPE = 0; // ENCRYPTION_NONE
    public static final String PREFS_NAME = "SVMP_PREFERENCES";

    // used to determine the state of the connection to the Proxy server
    public static final int PROTOCOLSTATE_ERROR = -1;
    public static final int PROTOCOLSTATE_UNAUTHENTICATED = 0;
    public static final int PROTOCOLSTATE_AUTHENTICATING = 1;
    public static final int PROTOCOLSTATE_VMREADYWAIT = 2;
    public static final int PROTOCOLSTATE_GETSCREENINFO = 3;
    public static final int PROTOCOLSTATE_PROXYREADY = 4;

    // used to determine what the EncryptionType for each connection is
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_SSLTLS = 1;
    public static final int ENCRYPTION_SSLTLS_UNTRUSTED = 2;
}
