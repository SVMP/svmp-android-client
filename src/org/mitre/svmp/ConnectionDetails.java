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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import org.mitre.svmp.auth.AuthModuleRegistry;
import org.mitre.svmp.auth.IAuthModule;
import org.mitre.svmp.client.R;
import org.mitre.svmp.widgets.AuthModuleArrayAdapter;

/**
 * @author Joe Portner
 */
public class ConnectionDetails extends SvmpActivity {
    private static String TAG = ConnectionDetails.class.getName();

    // the ID of the ConnectionInfo we are updating (0 is a new ConnectionInfo)
    private int updateID = 0;

    // views
    private EditText
            descriptionView,
            usernameView,
            hostView,
            portView,
            domainView;
    private Spinner
            encryptionView,
            authTypeView;
    private IAuthModule[] authModules;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.connection_details);
    }

    @Override
    protected void populateLayout() {
        // get views
        descriptionView = (EditText) findViewById(R.id.connectionDetails_editText_description);
        usernameView = (EditText) findViewById(R.id.connectionDetails_editText_username);
        hostView = (EditText) findViewById(R.id.connectionDetails_editText_host);
        portView = (EditText) findViewById(R.id.connectionDetails_editText_port);
        domainView = (EditText) findViewById(R.id.connectionDetails_editText_domain);
        encryptionView = (Spinner) findViewById(R.id.connectionDetails_spinner_encryption);
        authTypeView = (Spinner) findViewById(R.id.connectionDetails_spinner_authType);

        // populate items for AuthType spinner
        authModules = AuthModuleRegistry.getAuthModules();
        AuthModuleArrayAdapter adapter = new AuthModuleArrayAdapter(this, authModules);
        authTypeView.setAdapter(adapter);

        // check if an existing ConnectionInfo ID was sent with the Intent
        Intent intent = getIntent();
        if( intent.hasExtra("id") ) {
            // get the ID that was sent with the Intent
            int id = intent.getIntExtra("id", 0);

            // get the associated ConnectionInfo
            ConnectionInfo connectionInfo = dbHandler.getConnectionInfo(id);

            // if ConnectionInfo is null, stop
            if( connectionInfo == null ) {
                Log.d(TAG, "Expected ConnectionInfo is null");
                finishMessage(R.string.connectionList_toast_notFound, RESULT_REPOPULATE);
            }
            // ConnectionInfo is good, continue
            else {
                // populate views based on ConnectionInfo
                descriptionView.setText(connectionInfo.getDescription());
                usernameView.setText(connectionInfo.getUsername());
                hostView.setText(connectionInfo.getHost());
                portView.setText(String.valueOf(connectionInfo.getPort()));
                domainView.setText(connectionInfo.getDomain());
                encryptionView.setSelection(connectionInfo.getEncryptionType());
                for (int i = 0; i < authModules.length; i++)
                    if (authModules[i].getAuthTypeID() == connectionInfo.getAuthType()) {
                        authTypeView.setSelection(i);
                        break;
                    }

                // flag so we know this is an update, not insert
                updateID = connectionInfo.getConnectionID();
            }
        }
        // this is a new connection, fill in the default port
        else
            portView.setText(String.valueOf(DEFAULT_PORT));
    }

    // called onResume so preference changes take effect in the layout
    @Override
    protected void refreshPreferences() {
    }

    // save button is clicked
    public void onClick_Save(View v) {
        // get user input
        String description = descriptionView.getText().toString(),
                username = usernameView.getText().toString(),
                host = hostView.getText().toString(),
                portString = portView.getText().toString(),
                domain = domainView.getText().toString();
        int port = 0;
        try {
            port = Integer.parseInt(portString);
        } catch( NumberFormatException e ) {
            // don't care
        }
        int encryptionType = encryptionView.getSelectedItemPosition(),
                authType = authModules[authTypeView.getSelectedItemPosition()].getAuthTypeID();

        // validate input
        if( port < 1 || port > 65535 )
            toastShort(R.string.connectionDetails_toast_invalidPort);
        else if( description.length() == 0 )
            toastShort(R.string.connectionDetails_toast_blankDescription);
        else if( dbHandler.getConnectionInfo(updateID, description) != null )
            toastShort(R.string.connectionDetails_toast_ambiguousDescription);
        else if( username.length() == 0 )
            toastShort(R.string.connectionDetails_toast_blankUsername);
        else if( host.length() == 0 )
            toastShort(R.string.connectionDetails_toast_blankHost);
        else {
            // create a new ConnectionInfo object
            ConnectionInfo connectionInfo = new ConnectionInfo(updateID, description, username, host, port,
                    encryptionType, domain, authType);

            // insert or update the ConnectionInfo in the database
            long result;
            if( updateID > 0 )
                result = dbHandler.updateConnectionInfo(connectionInfo);
            else
                result = dbHandler.insertConnectionInfo(connectionInfo);

            // exit and resume previous activity, report results in the intent
            if( result > -1 && updateID > 0 )
                finishMessage(R.string.connectionList_toast_updated, RESULT_REPOPULATE);
            else if( result > -1 )
                finishMessage(R.string.connectionList_toast_added, RESULT_REPOPULATE);
            else
                finishMessage(R.string.connectionList_toast_error, RESULT_REPOPULATE);
        }
    }

    // cancel button is clicked
    public void onClick_Cancel(View v) {
        finish();
    }
}