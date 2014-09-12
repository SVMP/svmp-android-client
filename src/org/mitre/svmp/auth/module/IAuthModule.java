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
public interface IAuthModule {
    // integer ID used to identify this module
    int getID();

    // adds elements to the Dialog window for this Auth module, if necessary
    View generateUI(Context context);

    // method used to add the correct input to an Intent, may use input from a View if necessary
    void addRequestData(JSONObject jsonObject, View view);
}
