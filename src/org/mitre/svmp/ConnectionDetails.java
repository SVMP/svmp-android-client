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

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import org.mitre.svmp.auth.AuthRegistry;
import org.mitre.svmp.auth.module.CertificateModule;
import org.mitre.svmp.auth.type.IAuthType;
import org.mitre.svmp.client.R;
import org.mitre.svmp.widgets.AuthModuleArrayAdapter;

/**
 * @author Joe Portner
 */
public class ConnectionDetails extends SvmpActivity {
    private static String TAG = ConnectionDetails.class.getName();

    // the ID of the ConnectionInfo we are updating (0 is a new ConnectionInfo)
    private int updateID = 0;
    private boolean certAliasIsSet = false; // determines whether a valid certificate is selected

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
    private Button certificateView;
    private IAuthType[] authTypes;

    public void onCreate(Bundle savedInstanceState) {
        this.repopulateOnResume = false;
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
        certificateView = (Button) findViewById(R.id.connectionDetails_button_certificate);

        // populate items for AuthType spinner
        authTypes = AuthRegistry.getAuthTypes();
        AuthModuleArrayAdapter adapter = new AuthModuleArrayAdapter(this, authTypes);
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
                for (int i = 0; i < authTypes.length; i++)
                    if (authTypes[i].getID() == connectionInfo.getAuthType()) {
                        authTypeView.setSelection(i);
                        break;
                    }
                if (connectionInfo.getCertificateAlias().length() > 0)
                    setCertAlias(connectionInfo.getCertificateAlias());
                // flag so we know this is an update, not insert
                updateID = connectionInfo.getConnectionID();
            }
        }
        // this is a new connection, fill in the default port
        else
            portView.setText(String.valueOf(DEFAULT_PORT));

        if (!API_ICS) {
            // the SDK is lower than ICS, remove the Certificate selection table rows
            findViewById(R.id.connectionDetails_tableRow_certificate_1).setVisibility(View.GONE);
            findViewById(R.id.connectionDetails_tableRow_certificate_2).setVisibility(View.GONE);
        }
        else {
            // add listener for AuthType spinner
            authTypeView.setOnItemSelectedListener(authTypeSelectedListener);

            // check if the selected AuthType uses the CertificateModule
            boolean enabled = checkAuthTypeCert(authTypeView.getSelectedItemPosition());
            // set the Certificate button to either enabled or disabled, based on the AuthType
            certificateView.setEnabled(enabled);
        }
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
                authType = authTypes[authTypeView.getSelectedItemPosition()].getID();

        String certificateAlias = getCertificateAlias();
        boolean certAuthType = checkAuthTypeCert(authTypeView.getSelectedItemPosition());

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
        else if( encryptionType == ENCRYPTION_NONE && certAuthType )
            toastShort(R.string.connectionDetails_toast_certAuthNeedsSsl);
        else if( certAuthType && !certAliasIsSet)
            toastShort(R.string.connectionDetails_toast_certAuthNeedsAlias);
        else {
            // create a new ConnectionInfo object
            ConnectionInfo connectionInfo = new ConnectionInfo(updateID, description, username, host, port,
                    encryptionType, domain, authType, certificateAlias);

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

    // certificate button is clicked
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void onClick_Certificate(View v) {
        if (API_ICS) {
            // get the prior certificate alias (if it exists)
            String certificateAlias = getCertificateAlias();
            if (certificateAlias.length() == 0)
                certificateAlias = null;

            KeyChain.choosePrivateKeyAlias(this,
                    new KeyChainAliasCallback() {
                        @Override
                        public void alias(String s) {
                            final String alias = s;
                            // you can only modify views from the UI thread
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setCertAlias(alias);
                                }
                            });
                        }
                    }, // KeyChainAliasCallback
                    null, // any key type
                    null, // any issuer
                    null, // any host
                    -1, // any port
                    certificateAlias // alias to preselect if available
            );
        }
    }

    // detects when the AuthType is changed
    private OnItemSelectedListener authTypeSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            // check if the selected AuthType uses the CertificateModule
            boolean enabled = checkAuthTypeCert(position);
            // set the Certificate button to either enabled or disabled, based on the AuthType
            certificateView.setEnabled(enabled);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parentView) {
            // set the Certificate button to disabled
            certificateView.setEnabled(false);
        }
    };

    // check if the selected AuthType uses the CertificateModule
    private boolean checkAuthTypeCert(int position) {
        return (authTypes[position].getID() & CertificateModule.AUTH_MODULE_ID) == CertificateModule.AUTH_MODULE_ID;
    }

    private String getCertificateAlias() {
        String certificateAlias = certificateView.getText().toString();
        if (!certAliasIsSet)
            certificateAlias = "";
        return certificateAlias;
    }

    private void setCertAlias(String s) {
        certAliasIsSet = s != null;
        if (certAliasIsSet)
            certificateView.setText(s);
        else
            certificateView.setText(R.string.connectionDetails_button_certificate_none_text);
    }
}
