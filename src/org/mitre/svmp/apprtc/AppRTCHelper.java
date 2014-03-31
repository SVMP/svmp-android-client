package org.mitre.svmp.apprtc;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection.IceServer;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Joe Portner
 * Contains a number of helper functions used throughout AppRTC code
 */
public class AppRTCHelper {
    private static final String TAG = AppRTCHelper.class.getName();

    /**
     * Callback interface for messages delivered on the Google AppEngine channel.
     *
     * Methods are guaranteed to be invoked on the UI thread of |activity| passed
     * to GAEChannelClient's constructor.
     */
    public static interface MessageHandler {
        public void onOpen();
        public void onMessage(SVMPProtocol.Response data);
        public void onClose();
        public void onError(int code, String description);
    }

    /**
     * Callback fired once the room's signaling parameters specify the set of
     * ICE servers to use.
     */
    public static interface IceServersObserver {
        public void onIceServers(List<IceServer> iceServers);
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to put key/value pair in JSON: " + e.getMessage());
        }
    }

    public static AppRTCSignalingParameters getParametersForRoom(SVMPProtocol.VideoStreamInfo info) {
        MediaConstraints pcConstraints = constraintsFromJSON(info.getPcConstraints());
        Log.d(TAG, "pcConstraints: " + pcConstraints);

        MediaConstraints videoConstraints = constraintsFromJSON(info.getVideoConstraints());
        Log.d(TAG, "videoConstraints: " + videoConstraints);

        LinkedList<IceServer> iceServers = iceServersFromPCConfigJSON(info.getIceServers());

        return new AppRTCSignalingParameters(iceServers, true, pcConstraints, videoConstraints);
    }

    private static MediaConstraints constraintsFromJSON(String jsonString) {
        MediaConstraints constraints = new MediaConstraints();
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONObject mandatoryJSON = json.optJSONObject("mandatory");
            if (mandatoryJSON != null) {
                JSONArray mandatoryKeys = mandatoryJSON.names();
                if (mandatoryKeys != null) {
                    for (int i = 0; i < mandatoryKeys.length(); ++i) {
                        String key = mandatoryKeys.getString(i);
                        String value = mandatoryJSON.getString(key);
                        constraints.mandatory.add(
                                new MediaConstraints.KeyValuePair(key, value));
                    }
                }
            }
            JSONArray optionalJSON = json.optJSONArray("optional");
            if (optionalJSON != null) {
                for (int i = 0; i < optionalJSON.length(); ++i) {
                    JSONObject keyValueDict = optionalJSON.getJSONObject(i);
                    String key = keyValueDict.names().getString(0);
                    String value = keyValueDict.getString(key);
                    constraints.optional.add(
                            new MediaConstraints.KeyValuePair(key, value));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse MediaConstraints from JSON: " + e.getMessage());
        }
        return constraints;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private static LinkedList<IceServer> iceServersFromPCConfigJSON(String pcConfig) {
        LinkedList<IceServer> ret = new LinkedList<IceServer>();
        try {
            JSONObject json = new JSONObject(pcConfig);
            JSONArray servers = json.getJSONArray("iceServers");
            for (int i = 0; i < servers.length(); ++i) {
                JSONObject server = servers.getJSONObject(i);
                String url = server.getString("url");
                String credential =
                        server.has("credential") ? server.getString("credential") : "";
                ret.add(new IceServer(url, "", credential));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse ICE Servers from PC Config JSON: " + e.getMessage());
        }
        return ret;
    }
}
