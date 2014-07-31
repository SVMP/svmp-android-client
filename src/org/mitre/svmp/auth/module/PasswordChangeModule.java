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
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.mitre.svmp.client.R;
import org.mitre.svmp.protocol.SVMPProtocol.AuthRequest;

/**
 * @author Joe Portner
 * Special module to construct a password change dialog
 */
public class PasswordChangeModule implements IAuthModule {
    public static final int AUTH_MODULE_ID = 0; // This is a special module, not added to the AuthRegistry

    private EditText input1;
    private EditText input2;
    private EditText oldPasswordInput;

    public int getID() {
        return AUTH_MODULE_ID;
    }

    public View generateUI(Context context) {
        // create the password change inputs
        input1 = new EditText(context);
        input1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input1.setHint("New password");
        input2 = new EditText(context);
        input2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input2.setHint("Confirm new password");

        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(input1);
        view.addView(input2);

        return view;
    }

    public void addRequestData(AuthRequest.Builder builder, View view) {
        if (input1 != null && input2 != null) {
            String text = input1.getEditableText().toString();
            builder.setNewPassword(text);

            // by default, auth request type is "AUTHENTICATION"... change it
            builder.setType(AuthRequest.AuthRequestType.PASSWORD_CHANGE);
        }
    }

    // this constructor is unique to the PasswordChangeModule
    public PasswordChangeModule(View oldPasswordView) {
        this.oldPasswordInput = (EditText)oldPasswordView;
    }

    // this function is unique to the PasswordChangeModule, ensures that the new password input is valid
    // returns 0 for valid, otherwise returns a message resId for a toast to show to the user
    // should only be called after "generateUI"!
    public int areInputsValid() {
        int resId = 0;
        if (input1 != null && input2 != null) {
            String newPassword = input1.getEditableText().toString();
            if (!newPassword.equals(input2.getEditableText().toString()))
                resId = R.string.svmpActivity_toast_newPasswordFail_noMatch;
            else if (newPassword.length() < 8)
                resId = R.string.svmpActivity_toast_newPasswordFail_tooShort;
            else if (oldPasswordInput != null && newPassword.equals(oldPasswordInput.getEditableText().toString()))
                resId = R.string.svmpActivity_toast_newPasswordFail_same;
        }
        return resId;
    }
}
