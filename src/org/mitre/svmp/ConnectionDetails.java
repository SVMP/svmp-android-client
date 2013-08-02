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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import org.mitre.svmp.client.R;

/**
 * @author Joe Portner
 */
public class ConnectionDetails extends SvmpActivity implements Constants {
    private static String TAG = ConnectionDetails.class.getName();

    // database handler
    private DatabaseHandler dbHandler;
    private int updateID = 0;

    // views
    private EditText
            descriptionView,
            usernameView,
            hostView,
            portView;
    private Spinner encryptionView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set the layout
        setContentView(R.layout.connection_details);

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // get views
        descriptionView = (EditText) findViewById(R.id.connectionDetails_editText_description);
        usernameView = (EditText) findViewById(R.id.connectionDetails_editText_username);
        hostView = (EditText) findViewById(R.id.connectionDetails_editText_host);
        portView = (EditText) findViewById(R.id.connectionDetails_editText_port);
        encryptionView = (Spinner) findViewById(R.id.connectionDetails_spinner_encryption);

        // create listeners for buttons
        Button saveView = (Button) findViewById(R.id.button_save),
                cancelView = (Button) findViewById(R.id.button_cancel);
        saveView.setOnClickListener(saveHandler);
        cancelView.setOnClickListener(cancelHandler);

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
                finish();
            }
            // ConnectionInfo is good, continue
            else {
                // populate views based on ConnectionInfo
                descriptionView.setText(connectionInfo.getDescription());
                usernameView.setText(connectionInfo.getUsername());
                hostView.setText(connectionInfo.getHost());
                portView.setText(String.valueOf(connectionInfo.getPort()));
                encryptionView.setSelection(connectionInfo.getEncryptionType());

                // flag so we know this is an update, not insert
                updateID = connectionInfo.getID();
            }
        }
        // this is a new connection, fill in the default port
        else
            portView.setText(String.valueOf(DEFAULT_PORT));
    }

    // save button is clicked
    View.OnClickListener saveHandler = new View.OnClickListener() {
        public void onClick(View v) {
            // get user input
            String description = descriptionView.getText().toString(),
                    username = usernameView.getText().toString(),
                    host = hostView.getText().toString(),
                    portString = portView.getText().toString();
            int port = 0;
            try {
                port = Integer.parseInt(portString);
            } catch( NumberFormatException e ) {
                // don't care
            }
            int encryptionType = encryptionView.getSelectedItemPosition();

            // validate input
            if( port < 1 || port > 65535 )
                doToast("Invalid port number (must be 1 to 65535)");
            else if( description.length() == 0 )
                doToast("Description must not be blank");
            else if( ambiguousDescription(description) )
                doToast("That description is already used");
            else if( username.length() == 0 )
                doToast("Username must not be blank");
            else if( host.length() == 0 )
                doToast("Host must not be blank");
            else {
                // create a new ConnectionInfo object
                ConnectionInfo connectionInfo = new ConnectionInfo(updateID, description, username, host, port, encryptionType);

                // insert or update the ConnectionInfo in the database
                long result;
                if( updateID > 0 )
                    result = dbHandler.updateConnectionInfo(connectionInfo);
                else
                    result = dbHandler.insertConnectionInfo(connectionInfo);

                // exit and resume previous activity, report results in the intent
                if( result > -1 && updateID > 0 )
                    finishMessage(R.string.connectionList_toast_updated);
                else if( result > -1 )
                    finishMessage(R.string.connectionList_toast_added);
                else
                    finishMessage(R.string.connectionList_toast_error);
            }
        }
    };

    // cancel button is clicked
    View.OnClickListener cancelHandler = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    private boolean ambiguousDescription(String description) {
        return dbHandler.getConnectionInfo(updateID, description) != null;
    }

    private void finishMessage(int resId) {
        Intent intent = new Intent();
        intent.putExtra("message", resId);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void doToast(String text) {
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }
}