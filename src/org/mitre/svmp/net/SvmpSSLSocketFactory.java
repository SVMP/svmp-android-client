package org.mitre.svmp.net;

import org.apache.http.conn.ssl.SSLSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;

/**
 * This is used to configure SSL options for the HttpClient (interacts with the REST auth API)
 * @author Joe Portner
 */
public class SvmpSSLSocketFactory extends SSLSocketFactory {
    private SSLContext sslContext;
    private String[] enabledCiphers;
    private String[] enabledProtocols;

    public SvmpSSLSocketFactory(SSLContext sslContext, String[] enabledCiphers, String[] enabledProtocols)
            throws java.security.NoSuchAlgorithmException, java.security.KeyManagementException,
            java.security.KeyStoreException, java.security.UnrecoverableKeyException {
        super(null);
        this.sslContext = sslContext;
        this.enabledCiphers = enabledCiphers;
        this.enabledProtocols = enabledProtocols;
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        Socket value = sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        setExtras(value);
        return value;
    }

    @Override
    public Socket createSocket() throws IOException {
        Socket value = sslContext.getSocketFactory().createSocket();
        setExtras(value);
        return value;
    }

    private void setExtras(Socket socket) throws IOException {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket)socket;
            sslSocket.setEnabledCipherSuites(enabledCiphers);
            sslSocket.setEnabledProtocols(enabledProtocols);
            sslSocket.startHandshake(); // starts the handshake to verify the server cert before continuing
        }
    }
}
