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
package org.mitre.svmp.auth.module;

import android.content.Context;
import android.view.View;
import org.json.JSONObject;

/**
 * @author Joe Portner
 */
public class CertificateModule implements IAuthModule {
    public static final int AUTH_MODULE_ID = 1 << 2; // 4

    public int getID() {
        return AUTH_MODULE_ID;
    }

    public View generateUI(Context context) {
        // we don't need input for the certificate
        return null;
    }

    public void addRequestData(JSONObject jsonObject, View view) {
        // we don't need to add request data for the certificate, it's handled over the SSL handshake
    }
}
