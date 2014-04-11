package org.mitre.svmp.apprtc;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.VideoStreamInfo;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection.IceServer;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Joe Portner
 * Contains a number of helper functions used throughout AppRTC code
 */
public class AppRTCHelper {
    private static final String TAG = AppRTCHelper.class.getName();

    public static Request makeWebRTCRequest(JSONObject json) {
        WebRTCMessage.Builder rtcmsg = WebRTCMessage.newBuilder();
        rtcmsg.setJson(json.toString());

        return Request.newBuilder()
                .setType(Request.RequestType.WEBRTC)
                .setWebrtcMsg(rtcmsg)
                .build();
    }

    // Poor-man's assert(): die with |msg| unless |condition| is true.
    public static void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to put key/value pair in JSON: " + e.getMessage());
        }
    }

    public static AppRTCSignalingParameters getParametersForRoom(VideoStreamInfo info) {
        MediaConstraints pcConstraints = constraintsFromJSON(info.getPcConstraints());
        Log.d(TAG, "pcConstraints: " + pcConstraints);

        MediaConstraints videoConstraints = constraintsFromJSON(info.getVideoConstraints());
        Log.d(TAG, "videoConstraints: " + videoConstraints);

        LinkedList<IceServer> iceServers = iceServersFromPCConfigJSON(info.getIceServers());

        return new AppRTCSignalingParameters(iceServers, true, pcConstraints, videoConstraints);
    }

    // Mangle SDP to prefer ISAC/16000 over any other audio codec.
    public static String preferISAC(String sdpDescription) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String isac16kRtpMap = null;
        Pattern isac16kPattern =
                Pattern.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
        for (int i = 0;
             (i < lines.length) && (mLineIndex == -1 || isac16kRtpMap == null);
             ++i) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i;
                continue;
            }
            Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1);
                continue;
            }
        }
        if (mLineIndex == -1) {
            Log.d(TAG, "No m=audio line, so can't prefer iSAC");
            return sdpDescription;
        }
        if (isac16kRtpMap == null) {
            Log.d(TAG, "No ISAC/16000 line, so can't prefer iSAC");
            return sdpDescription;
        }
        String[] origMLineParts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int origPartIndex = 0;
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(isac16kRtpMap);
        for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
            if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
                newMLine.append(" ").append(origMLineParts[origPartIndex]);
            }
        }
        lines[mLineIndex] = newMLine.toString();
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
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
