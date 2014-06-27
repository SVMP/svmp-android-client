/*
 * Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mitre.svmp.activities;

import android.os.Bundle;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

/**
 * @author Joe Portner
 * Special activity to change a password and exit after completion
 */
public class AppRTCChangePasswordActivity extends AppRTCActivity {
    private static final String TAG = AppRTCChangePasswordActivity.class.getName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // MessageHandler interface method
    // Called when the client connection is established
    @Override
    public void onOpen() {
        super.onOpen();

        // we got past authentication successfully, finish the activity
        setResult(RESULT_OK);
        disconnectAndExit();
    }

    // MessageHandler interface method
    // Called when a message is sent from the server, and the SessionService doesn't consume it
    public boolean onMessage(Response data) {
        switch (data.getType()) {
            default:
                // any messages we don't understand, pass to our parent for processing
                super.onMessage(data);
        }
        return true;
    }
}
