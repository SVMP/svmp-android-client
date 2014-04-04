package org.mitre.svmp.apprtc;

import org.mitre.svmp.protocol.SVMPProtocol.Response;

/**
 * @author Joe Portner
 *
 * Callback interface for messages delivered on the Google AppEngine channel.
 *
 * Methods are guaranteed to be invoked on the UI thread of |activity| passed
 * to GAEChannelClient's constructor.
 */
public interface MessageHandler {
    public void onOpen();
    public boolean onMessage(Response data);
    // the methods below are not currently used
    //public void onClose();
    //public void onError(int code, String description);
}
