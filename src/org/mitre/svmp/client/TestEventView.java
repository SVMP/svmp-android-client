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

import java.util.Timer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.media.MediaPlayer;
import android.os.Parcel;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.VideoView;


/**
 * Base testing VIEW.  See ClientTestView for an implementation (use) of this class
 * @author Dave Bryson
 */
public class TestEventView extends SurfaceView {
    private static final String TAG = "TestEventView";
    
    

    //public static final int VIEW_BACKGROUND = Color.rgb(214, 214, 214);
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestures;
    private final FlingAnimator flingAnimator = new FlingAnimator();
    private final Interpolator interpolator = new DecelerateInterpolator();

    //private ShapeDrawable oval = new ShapeDrawable(new OvalShape());
    private static final int half_size = 100;
    private int centerX, centerY;
    private float scaleFactor = 1.0f;
    private float pinchX, pinchY;

   
    
    public TestEventView(Context context) {
        super(context);
        setUp(context);
    }

    public TestEventView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setUp(context);
    }

    public TestEventView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        setUp(context);
    }
   
    
    
   

    private void setUp(final Context context) {
        gestureDetector = new GestureDetector(new TouchGestures());
        scaleGestures = new ScaleGestureDetector(context, new PinchZoomScaleGesture());
        //oval.getPaint().setColor(0xff74AC23);
        //oval.setBounds(centerX - half_size, centerY - half_size, centerX + half_size, centerY + half_size);
    }

    protected void pan(final float dx, final float dy) {
        centerX -= dx;
        centerY -= dy;

        // bound it
        final int w = getWidth();
        final int h = getHeight();
        if (centerX < 0)
            centerX = 0;
        if (centerX >= w)
            centerX = w;
        if (centerY < 0)
            centerY = 0;
        if (centerY >= h)
            centerY = h;
        invalidate();
    }

    public boolean onTouchEvent(final MotionEvent event) {
        scaleGestures.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        //Log.d(TAG,event.toString());
        //logMotionEvent(event);
        final Parcel p = Parcel.obtain();
        event.writeToParcel(p,0);
        final byte[] data = p.marshall();
        evalEvent(data);
        return true;
    }

    // view is made transparent by commenting this out
    // want the VideoView to show through from behind
  /*  public void onDraw(final Canvas canvas) {
        canvas.drawColor(VIEW_BACKGROUND);
        canvas.save();
        if (scaleFactor != 1.0f) {
            canvas.scale(scaleFactor, scaleFactor, pinchX, pinchY);
        }
        oval.setBounds(centerX - half_size, centerY - half_size, centerX + half_size, centerY + half_size);
        oval.draw(canvas);
        canvas.restore();
    }*/

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        this.centerX = w >> 1;
        this.centerY = h >> 1;
    }

    private class FlingAnimator {
        private long startTime;
        private long endTime;
        private float totalAnimDx;
        private float totalAnimDy;

        public void doFling(final float dX, final float dY, final long d) {
            startTime = System.currentTimeMillis();
            endTime = startTime + d;
            totalAnimDx = dX;
            totalAnimDy = dY;
            TestEventView.this.post(new Runnable() {
                public void run() {
                    stepFling();
                }
            });
        }

        public void stepFling() {
            long curTime = System.currentTimeMillis();
            float percentTime = (float) (curTime - startTime) / (float) (endTime - startTime);
            float adjustedTime = (percentTime > 1.0f) ? 1.0f : percentTime;
            float percentDistance = interpolator.getInterpolation(adjustedTime);
            float curDx = percentDistance * totalAnimDx;
            float curDy = percentDistance * totalAnimDy;
            TestEventView.this.pan(-curDx, -curDy);
            if (percentTime < 1.0f) {
                post(new Runnable() {
                    public void run() {
                        stepFling();
                    }
                });
            }
        }
    }


    private class TouchGestures extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent evt) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent evt) {
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            final float distanceTimeFactor = 0.07f;
            final float totalDx = (distanceTimeFactor * velocityX / 2);
            final float totalDy = (distanceTimeFactor * velocityY / 2);
            flingAnimator.doFling(totalDx, totalDy, (long) (1000 * distanceTimeFactor));
            return true;
        }

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!scaleGestures.isInProgress()) {
                pan(distanceX, distanceY);
            }
            return true;
        }
    }


    private class PinchZoomScaleGesture extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            scaleFactor = 1.0f;
            pinchX = detector.getFocusX();
            pinchY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector sg) {
            scaleFactor *= sg.getScaleFactor();
            // TODO: translate if moving...See if the person is moving the center
            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaleFactor = 1f;
            invalidate();
        }
    }

    private void evalEvent(final byte[] data){
        final Parcel p = Parcel.obtain();
        p.unmarshall(data,0,data.length);
        final MotionEvent m2 = MotionEvent.CREATOR.createFromParcel(p);
        Log.d(TAG,m2.toString());
    }

    private void logMotionEvent(final MotionEvent event) {
        final String names[] = {"DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE","POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?"};
        StringBuilder sb = new StringBuilder();
        final int action = event.getAction();
        final int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_").append(names[actionCode]);
        if (actionCode == MotionEvent.ACTION_POINTER_DOWN || actionCode == MotionEvent.ACTION_POINTER_UP) {
            sb.append("(pid ").append(action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
            sb.append(")");
        }
        sb.append("[");
        for (int i = 0; i < event.getPointerCount(); i++) {
            sb.append("#").append(i);
            sb.append("(pid ").append(event.getPointerId(i));
            sb.append(")=").append((int) event.getX(i));
            sb.append(",").append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";");
        }
        sb.append("]");
        Log.d(TAG, sb.toString());
    }

}
