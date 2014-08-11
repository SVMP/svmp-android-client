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
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Joe Portner
 */
public class SecurityTokenModule implements IAuthModule {
    private static final String TAG = SecurityTokenModule.class.getName();

    public static final int AUTH_MODULE_ID = 1 << 1; // 2

    public int getID() {
        return AUTH_MODULE_ID;
    }

    public View generateUI(Context context) {
        // create the token input
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Security Token");
        return input;
    }

    public void addRequestData(JSONObject jsonObject, View view) {
        EditText input = (EditText)view;
        if (input != null && input.getEditableText() != null) {
            String text = input.getEditableText().toString();
            try {
                jsonObject.put("securityToken", text);
            } catch (JSONException e) {
                Log.e(TAG, "addRequestData failed:", e);
            }
        }
        else {
            Log.e(TAG, "addRequestData failed: input is null");
        }
    }
}
