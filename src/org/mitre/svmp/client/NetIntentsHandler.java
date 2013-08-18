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

import org.mitre.svmp.AppRTCDemoActivity;
import org.mitre.svmp.client.R;
import org.mitre.svmp.protocol.SVMPProtocol;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Looper;
import android.widget.Toast;

public class NetIntentsHandler
{
	public static final String TAG = "NetIntentsClient";
	
	//If this is a NOTIFICATION or INTENT, then process it accordingly.
	public static void inspect(SVMPProtocol.Response response, Context context)
	{
		switch(response.getType())
		{
		case INTENT:
			org.mitre.svmp.protocol.SVMPProtocol.Intent intent = response.getIntent();
			switch(intent.getAction())
			{
			case ACTION_DIAL:
				toast("Dialing "+intent.getData(),context);
				Intent dial = new Intent();
				dial.setAction("android.intent.action.DIAL");
				dial.setData(Uri.parse(intent.getData()));
				dial.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				context.startActivity(dial);
				break;
			case ACTION_VIEW:
				//TODO: error, or just ignore?
				break;
			default:
				break;
			}
			break;
		case NOTIFICATION:
			org.mitre.svmp.protocol.SVMPProtocol.Notification notification = response.getNotification();
	        // Prepare intent which is triggered if the notification is selected.
			toast("Notification received!",context);
	    	    
	    	//Build intents.
	        Intent inner_intent = new Intent(context, AppRTCDemoActivity.class);
	        PendingIntent pIntent = PendingIntent.getActivity(context, 0, inner_intent, 0);
	        
	        //Decode image
	        byte[] data = notification.getSmallIcon().toByteArray();
	        Bitmap bmp = BitmapFactory.decodeByteArray(data,0, data.length);

	        // Build notification.
	        Notification noti = new Notification.Builder(context)
	            .setContentTitle(notification.getContentTitle())
	            .setContentText(notification.getContentText())
	            .setSmallIcon(R.drawable.logo)
	            .setLargeIcon(bmp.copy(Bitmap.Config.ARGB_8888, true))
	            .setContentIntent(pIntent).getNotification();
	        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	            
	        // Hide the notification after its selected.
	        noti.flags |= Notification.FLAG_AUTO_CANCEL;
	        notificationManager.notify(0, noti);
			break;
		default:
			break;
		}
	}
	
	public static void toast(final String message,final Context context)
	{
		new Thread(new Runnable(){public void run(){Looper.prepare();
		Toast.makeText(context,"!!DEBUG_SVMP!!:"+message,Toast.LENGTH_SHORT).show();
		Looper.loop();}}).start();
	}
}
