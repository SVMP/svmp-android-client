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

import android.view.KeyEvent;
import org.mitre.svmp.activities.AppRTCActivity;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Request;

/**
 * @author Joe Portner
 * Captures key events to send to the remote SVMP instance.
 */
public class KeyHandler implements Constants {
    private AppRTCActivity activity;

    public KeyHandler(AppRTCActivity activity) {
        this.activity = activity;
    }

    public boolean tryConsume(KeyEvent event) {
        // whenever any key is pressed, catch the event and track it
        // note: can't catch Home, Search, and App Switch keys within an app
        if (activity.isConnected()) {
            Request request = makeRequest(event);
            activity.sendMessage(request);
            return true; // consume the event
        }

        return false; // don't consume the event, pass it onto other handler(s)
    }

    // transforms KeyEvent into a Request
    private Request makeRequest(KeyEvent event) {
        SVMPProtocol.KeyEvent.Builder kBuilder = SVMPProtocol.KeyEvent.newBuilder();
        kBuilder.setEventTime(event.getEventTime());
        kBuilder.setDeviceId(event.getDeviceId());
        kBuilder.setFlags(event.getFlags());

        if (event.getAction() == KeyEvent.ACTION_MULTIPLE && event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            // this attribute is used for the special case of a ACTION_MULTIPLE event with key code of KEYCODE_UNKNOWN
            kBuilder.setCharacters(event.getCharacters());
        }
        else {
            // the following attributes are used whenever action is not ACTION_MULTIPLE, OR key code is not KEYCODE_UNKNOWN
            kBuilder.setDownTime(event.getDownTime());
            kBuilder.setAction(event.getAction());
            kBuilder.setCode(event.getKeyCode());
            kBuilder.setRepeat(event.getRepeatCount());
            kBuilder.setMetaState(event.getMetaState());
            kBuilder.setScanCode(event.getScanCode());
            kBuilder.setSource(event.getSource());
        }

        // wrap the KeyEvent in a Request and return it
        Request.Builder rBuilder = Request.newBuilder();
        rBuilder.setType(Request.RequestType.KEYEVENT);
        rBuilder.setKey(kBuilder);
        return rBuilder.build();
    }
}
