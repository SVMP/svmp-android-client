package org.mitre.svmp.apprtc;

import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;

import java.util.List;

/**
 * @author Joe Portner
 * Struct holding the signaling parameters of an AppRTC room
 */
public class AppRTCSignalingParameters {
    public final List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final MediaConstraints pcConstraints;
    public final MediaConstraints videoConstraints;

    public AppRTCSignalingParameters(
            List<PeerConnection.IceServer> iceServers,
            boolean initiator, MediaConstraints pcConstraints,
            MediaConstraints videoConstraints) {
        this.initiator = initiator;
        this.iceServers = iceServers;
        this.pcConstraints = pcConstraints;
        this.videoConstraints = videoConstraints;
    }
}
