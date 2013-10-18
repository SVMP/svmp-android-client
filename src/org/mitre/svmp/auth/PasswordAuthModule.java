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

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import org.mitre.svmp.protocol.SVMPProtocol.AuthRequest;

/**
 * @author Joe Portner
 */
public class PasswordAuthModule implements IAuthModule {
    private static int AUTH_TYPE_ID = 0;

    public int getAuthTypeID() {
        return AUTH_TYPE_ID;
    }

    public String getAuthTypeDescription() {
        return "Password only";
    }

    public boolean isModuleUsed(int authTypeID) {
        return true; // a password is always required
    }

    public View generateUI(Context context) {
        // create the token input
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");
        return input;
    }

    public void addRequestData(AuthRequest.Builder builder, View view, int authTypeID) {
        if (authTypeID == AUTH_TYPE_ID)
            builder.setType(AuthRequest.AuthRequestType.PASSWORD);

        EditText input = (EditText)view;
        String text = input.getEditableText().toString();
        builder.setPassword(text);
    }
}
