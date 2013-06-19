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
package org.mitre.svmp.client;


import org.mitre.svmp.AuthData;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Starting Activity.
 */
public class MainClientActivity extends Activity {
    private static final String TAG = "MainClientActivity";
    public static final String PREFS_NAME = "VIRTUAL_PREF";
    private SharedPreferences settings;
    private EditText host;
    private EditText port;
    private static final int BAD_INPUT_DIALOG = 0;
    //private ClientSideActivityDirect testClient;
    private Boolean videostarted;
    private Intent videointent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final Context context = this;
        // Check preferences
        settings = getSharedPreferences(PREFS_NAME, 0);
        final String host1 = settings.getString("host", "");
        final int port1 = settings.getInt("port", 8002);

        Button button = (Button) findViewById(R.id.btnLogin);
        host = (EditText) findViewById(R.id.host);
        port = (EditText) findViewById(R.id.port);

        host.setText(host1);
        port.setText(Integer.toString(port1));
        videointent = new Intent(getApplicationContext(), ClientSideActivityDirect.class);
       
        videostarted=false;

        // Register the onClick listener with the implementation above
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                final String h = host.getText().toString();
                final String p = port.getText().toString();

                // Validate port number
                int actualPortNumer;
                try {
                    actualPortNumer = Integer.parseInt(p);
                    // Save to preferences
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("host", h);
                    editor.putInt("port", actualPortNumer);

                    // Launch the ClientSideActivity
                    AuthData.init( ((EditText)findViewById(R.id.username)).getText().toString(),
                                   ((EditText)findViewById(R.id.password)).getText().toString() );
                    videointent.putExtra("host", h);
                    videointent.putExtra("port", actualPortNumer);
                    
                    startActivity(videointent);
                    videostarted=true;

                } catch (NumberFormatException e) {
                    showDialog(BAD_INPUT_DIALOG);
                }
            }
        });
    }
     
    @Override
    public void onStart(){
    	super.onStart();
    	Log.e(TAG,"onStart()");
    	if(videostarted == true) {
    		  startActivity(videointent);    		
    	}
    }
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch (id) {
            case BAD_INPUT_DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Invalid host or port number")
                        .setCancelable(false)
                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });

                dialog = builder.create();
                break;
            default:
                dialog = null;
        }
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
