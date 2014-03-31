/*
 * Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.svmp.client;

import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.performance.PerformanceAdapter;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Request.RequestType;

import android.graphics.Point;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Input capture view. Sets itself in the foreground and captures touch input events
 * to be sent to a remote SVMP instance.
 */
public class TouchHandler implements Constants {

    private static final String TAG = TouchHandler.class.getName();
    
    private AppRTCActivity activity;
    private PerformanceAdapter spi;
    private Point displaySize;
    
    private float xScaleFactor, yScaleFactor = 0;
    private boolean gotScreenInfo = false;

    public TouchHandler(AppRTCActivity activity, Point displaySize, PerformanceAdapter spi) {
//        super(context);
        
        this.activity = activity;
        this.displaySize = displaySize;
        this.spi = spi;

        // make sure we're on top and have input focus
//        bringToFront();
//        requestFocus();
//        requestFocusFromTouch();
//        setClickable(true);
    }
    
    public void sendScreenInfoMessage() {
        SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
        msg.setType(RequestType.SCREENINFO);
        
        activity.sendMessage(msg.build());
        Log.d(TAG, "Sent screen info request");
    }
    
    public boolean handleScreenInfoResponse(SVMPProtocol.Response msg) {
        if (!msg.hasScreenInfo())
            return false;
        
        final int x = msg.getScreenInfo().getX();
        final int y = msg.getScreenInfo().getY();

        Log.d(TAG, "Got the ServerInfo: xsize=" + x + " ; ysize=" + y);
        this.xScaleFactor = (float)x/(float)displaySize.x;
        this.yScaleFactor = (float)y/(float)displaySize.y;
        Log.i(TAG, "Scale factor: " + xScaleFactor + " ; " + yScaleFactor);

        gotScreenInfo = true;
        
        return true;
    }

//    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (! gotScreenInfo) return false;

        // increment the touch update count for performance measurement
        spi.incrementTouchUpdates();

        // SEND REMOTE EVENT
        SVMPProtocol.Request.Builder msg = SVMPProtocol.Request.newBuilder();
        SVMPProtocol.TouchEvent.Builder eventmsg = SVMPProtocol.TouchEvent.newBuilder();
        SVMPProtocol.TouchEvent.PointerCoords.Builder p = SVMPProtocol.TouchEvent.PointerCoords.newBuilder();
                
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_POINTER_DOWN:
                eventmsg.setAction(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                break;
            case MotionEvent.ACTION_POINTER_UP:
                eventmsg.setAction(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                break;
            default:
                eventmsg.setAction(event.getActionMasked());
                break;
        }
        
        final int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            final float adjX = event.getX(i) * this.xScaleFactor;
            final float adjY = event.getY(i) * this.yScaleFactor;
            p.clear();
            p.setId(i);
            p.setX(adjX);
            p.setY(adjY);
            eventmsg.addItems(p.build());
        }

        msg.setType(RequestType.TOUCHEVENT);
        msg.setTouch(eventmsg);

        activity.sendMessage(msg.build());

        return true;
    }
}
