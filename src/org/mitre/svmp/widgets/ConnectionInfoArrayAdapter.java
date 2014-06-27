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
package org.mitre.svmp.widgets;

import android.content.Context;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.services.SessionService;

/**
 * @author Joe Portner
 */
public class ConnectionInfoArrayAdapter extends TwoLineArrayAdapter<ConnectionInfo> {
    public ConnectionInfoArrayAdapter(Context context, ConnectionInfo[] connectionInfos) {
        super(context, connectionInfos);
    }

    @Override
    public String lineOneText(ConnectionInfo connectionInfo) {
        return connectionInfo.lineOneText();
    }

    @Override
    public String lineTwoText(ConnectionInfo connectionInfo) {
        return connectionInfo.lineTwoText();
    }

    @Override
    public boolean isActive(ConnectionInfo connectionInfo) {
        return SessionService.isRunningForConn(connectionInfo.getConnectionID());
    }

    @Override
    public boolean hasEncryption(ConnectionInfo connectionInfo) {
        return connectionInfo.getEncryptionType() != Constants.ENCRYPTION_NONE;
    }

    @Override
    public String buttonText(ConnectionInfo connectionInfo) {
        return connectionInfo.buttonText();
    }
}