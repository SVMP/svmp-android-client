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
package org.mitre.svmp.widgets;

import android.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.mitre.svmp.auth.IAuthModule;

/**
 * @author Joe Portner
 */
public class AuthModuleArrayAdapter extends ArrayAdapter<IAuthModule> {
    private int mSpinnerLayoutResId;
    private int mSpinnerDropDownLayoutResId;

    public AuthModuleArrayAdapter(Context context, IAuthModule[] authModules) {
        this(context, R.layout.simple_spinner_item, R.layout.simple_spinner_dropdown_item, authModules);
    }

    public AuthModuleArrayAdapter(Context context, int spinnerResId, int spinnerDropDownResId, IAuthModule[] authModules) {
        super(context, spinnerResId, authModules);
        mSpinnerLayoutResId = spinnerResId;
        mSpinnerDropDownLayoutResId = spinnerDropDownResId;
        setDropDownViewResource(spinnerDropDownResId);
    }

    // make sure the selected item shows the correct text
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createView(mSpinnerLayoutResId, position, convertView, parent);
    }

    // make sure each entry in the drop down list shows the correct text
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createView(mSpinnerDropDownLayoutResId, position, convertView, parent);
    }

    private View createView(int resId, int position, View convertView, ViewGroup parent) {
        LayoutInflater layoutInflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = convertView;
        if (null == convertView) {
            view = layoutInflater.inflate(resId, parent, false);
        }

        // get the Auth Module at this position
        IAuthModule authModule = getItem(position);

        // set the text for the child view
        TextView text1 = (TextView)view.findViewById(android.R.id.text1);
        text1.setText(authModule.getAuthTypeDescription());

        return view;
    }
}
