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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import org.mitre.svmp.client.ClientSideActivityDirect;
import org.mitre.svmp.client.R;

import java.util.List;

/**
 * @author Joe Portner & David Schoenheit
 */
public class ConnectionList extends Activity implements Constants {
    private static String TAG = ConnectionList.class.getName();
    private static final int REQUEST_CODE = 100;

    private DatabaseHandler dbHandler;
    private List<ConnectionInfo> connectionInfoList;
    private ListView listView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set the layout
        setContentView(R.layout.connection_list);

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // get views
        listView = (ListView)findViewById(R.id.listView_connections);

        // create listeners for buttons
        Button temporaryView = (Button) findViewById(R.id.button_temporary),
                newView = (Button) findViewById(R.id.button_new),
                exitView = (Button) findViewById(R.id.button_exit);
        temporaryView.setOnClickListener(temporaryHandler);
        newView.setOnClickListener(newHandler);
        exitView.setOnClickListener(exitHandler);

        // create listeners for ListView
        listView.setOnItemClickListener( new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                try {
                    passwordPrompt((ConnectionInfo) listView.getItemAtPosition(position));
                } catch( Exception e ) {
                    // don't care
                }

            }
        });
        registerForContextMenu(listView);

        // populate the ListView
        populateConnections();
    }

    // connect now button is clicked (temporary, for development use)
    View.OnClickListener temporaryHandler = new View.OnClickListener() {
        public void onClick(View v) {

            // Check preferences
            final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            final String tempHostOctet = settings.getString("tempHostOctet", "");

            // create the password input
            final EditText input = new EditText(ConnectionList.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(tempHostOctet);

            // show a dialog
            new AlertDialog.Builder(ConnectionList.this)
                    .setTitle("Enter last octet of IP")
                    .setMessage("192.168.42.")
                    .setView(input)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // get value
                            Editable value = input.getText();

                            // save value in preferences
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString("tempHostOctet", value.toString());
                            editor.commit();

                            startVideo(
                                    "FAKEUSERNAME",
                                    "FAKEPASSWORD",
                                    "192.168.42." + value.toString(),
                                    DEFAULT_PORT);
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    };

    // new button is clicked
    View.OnClickListener newHandler = new View.OnClickListener() {
        public void onClick(View v) {
            Intent intent = new Intent(ConnectionList.this, ConnectionDetails.class);
            startActivityForResult(intent, REQUEST_CODE);
        }
    };

    // exit button is clicked
    View.OnClickListener exitHandler = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUEST_CODE ) {
            switch(resultCode) {
                case RESULT_ADDED:
                    doToast("New connection created");
                    break;
                case RESULT_UPDATED:
                    doToast("Connection updated");
                    break;
                case RESULT_ERROR:
                    doToast("Error creating new connection");
                    break;
            }

            if( resultCode == RESULT_ADDED || resultCode == RESULT_UPDATED) {
                // repopulate the ListView
                populateConnections();
            }
        }
    }

    private void passwordPrompt(final ConnectionInfo connectionInfo) {
        if( connectionInfo == null ) {
            Log.d(TAG, "Selected ConnectionInfo is null");
        }
        else {
            // create the password input
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            // show a dialog
            new AlertDialog.Builder(ConnectionList.this)
                    .setTitle("Password required")
                    .setMessage("Enter a password to proceed")
                    .setView(input)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Editable value = input.getText();

                            startVideo(
                                    connectionInfo.getUsername(),
                                    value.toString(),
                                    connectionInfo.getHost(),
                                    connectionInfo.getPort());
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    }).show();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(v.getId() == R.id.listView_connections){
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle(connectionInfoList.get(info.position).getDescription());
            String[] menuItems = getResources().getStringArray(R.array.connection_list_context_items);
            for(int i = 0; i < menuItems.length; i++){
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }
    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch(item.getItemId()){
            case 0:
                Intent intent = new Intent(ConnectionList.this, ConnectionDetails.class);
                intent.putExtra("id", connectionInfoList.get(info.position).getID());
                startActivityForResult(intent, REQUEST_CODE);
                break;
            case 1:
                dbHandler.deleteConnectionInfo(connectionInfoList.get(info.position).getID());
                populateConnections();
                doToast("Connection removed");
                break;
        }
        return true;
    }

    private void startVideo(String username, String password, String host, int port) {
        AuthData.init(username, password);
        Intent intent = new Intent(ConnectionList.this, ClientSideActivityDirect.class);
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        startActivity(intent);
    }

    private void populateConnections() {
        // get the list of ConnectionInfo objects
        connectionInfoList = dbHandler.getConnectionInfoList();
        // populate the items in the ListView
        listView.setAdapter(new ArrayAdapter<ConnectionInfo>(getApplicationContext(), android.R.layout.simple_list_item_1, connectionInfoList));
    }

    private void doToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}