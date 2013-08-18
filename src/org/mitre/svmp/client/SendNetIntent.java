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

//import org.mitre.svmp.RemoteServerClient;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.IntentAction;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class SendNetIntent extends Activity
{
	private static final String TAG = "SendNetIntent";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Put together the Intent protobuffer.
		Log.i(TAG,"GOING TO SEND URL INTENT with URL: "+getIntent().getDataString());
		SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
		SVMPProtocol.Intent.Builder intentProtoBuffer = SVMPProtocol.Intent.newBuilder();			
		intentProtoBuffer.setAction(IntentAction.ACTION_VIEW);
		intentProtoBuffer.setData(getIntent().getDataString());
			
		//Set the Request message params and send it off.
		msg.setType(RequestType.INTENT);
		msg.setIntent(intentProtoBuffer.build());
//		RemoteServerClient.sendMessage(msg.build());
		
		finish();
	}
}
