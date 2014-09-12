/*
 * Copyright (c) 2014 The MITRE Corporation, All Rights Reserved.
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
package org.mitre.svmp.net;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.google.PRNGFixes;
import de.duenndns.ssl.MemorizingTrustManager;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.mitre.svmp.auth.SVMPKeyManager;
import org.mitre.svmp.auth.module.CertificateModule;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.Utility;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * @author Joe Portner, Dave Keppler
 */
public class SSLConfig implements Constants {
    private static final String TAG = SSLConfig.class.getName();

    private ConnectionInfo connectionInfo;
    private Activity activity;
    private Context context;

    private SSLContext sslContext;
    private MemorizingTrustManager mtm;

    public SSLConfig(ConnectionInfo connectionInfo, Activity activity) {
        this.connectionInfo = connectionInfo;
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    // returns 0 if successful, otherwise returns resId for an error message
    public int configure() {
        // default return value is a generic SSL-related error
        int value = R.string.appRTC_toast_socketConnector_failSSL;

        // TODO: specific error messages?
        try {
            doConfigure();
            value = 0;
        } catch (KeyStoreException e) {
            Log.e(TAG, "SSLConfig failed:", e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SSLConfig failed:", e);
        } catch (CertificateException e) {
            Log.e(TAG, "SSLConfig failed:", e);
        } catch (IOException e) {
            Log.e(TAG, "SSLConfig failed:", e);
        } catch (KeyManagementException e) {
            Log.e(TAG, "SSLConfig failed:", e);
        }
        return value;
    }

    // only get SSLContext for Socket after it has been configured
    public SSLContext getSSLContext() {
        return sslContext;
    }

    // only get Apache SSLSocketFactory for HttpClient after it has been configured
    public SSLSocketFactory getSocketFactory() {
        SSLSocketFactory factory = null;
        try {
            factory = new SvmpSSLSocketFactory(sslContext, ENABLED_CIPHERS, ENABLED_PROTOCOLS);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "getSocketFactory failed:", e);
        } catch (KeyManagementException e) {
            Log.e(TAG, "getSocketFactory failed:", e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "getSocketFactory failed:", e);
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "getSocketFactory failed:", e);
        }
        return factory;
    }

    @SuppressLint("TrulyRandom")
    private void doConfigure() throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException, KeyManagementException {
        // find out if we should use the MemorizingTrustManager instead of the system trust store (set in Preferences)
        boolean useMTM = Utility.getPrefBool(context,
                R.string.preferenceKey_connection_useMTM,
                R.string.preferenceValue_connection_useMTM);

        // determine whether we should use client certificate authentication
        boolean useCertificateAuth = Constants.API_14 &&
                (connectionInfo.getAuthType() & CertificateModule.AUTH_MODULE_ID) == CertificateModule.AUTH_MODULE_ID;

        // set up key managers
        KeyManager[] keyManagers = null;
        // if certificate authentication is enabled, use a key manager with the provided alias
        if (useCertificateAuth) {
            keyManagers = new KeyManager[]{new SVMPKeyManager(context, connectionInfo.getCertificateAlias())};
        }

        // set up trust managers
        TrustManager[] trustManagers = null;

        KeyStore localTrustStore = KeyStore.getInstance("BKS");
        InputStream in = context.getResources().openRawResource(R.raw.client_truststore);
        localTrustStore.load(in, Constants.TRUSTSTORE_PASSWORD.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(localTrustStore);

        // 1) If "res/raw/client_truststore.bks" is not empty, use it as the pinned cert trust store (default is empty)
        // 2) Otherwise, if the "Show certificate dialog" developer preference is enabled, use that (default is disabled)
        // 3) Otherwise, use the default system trust store, consists of normal trusted Android CA certs
        if (localTrustStore.size() > 0) {
            // this means that "res/raw/client_truststore.bks" has been replaced with a trust store that is not empty
            // we will use that "pinned" store to check server certificate trust
            Log.d(TAG, "SSLConfig: Using static BKS trust store to check server cert trust");
            trustManagers = trustManagerFactory.getTrustManagers();
        // After switching to WebSockets, MTM causes the app to freeze; removed for now
        } else if (useMTM) {
            // by default useMTM is false ("Show certificate dialog" in developer preferences)
            // this creates a certificate dialog to decide what to do with untrusted certificates, instead of flat-out rejecting them
            Log.d(TAG, "SSLConfig: Static BKS trust store is empty but MTM is enabled, using MTM to check server cert trust");
            mtm = new MemorizingTrustManager(context);
            mtm.bindDisplayActivity(activity);
            trustManagers = new X509TrustManager[] {mtm};
        } else {
            Log.d(TAG, "SSLConfig: Static BKS trust store is empty and MTM is disabled, using system trust store to check server cert trust");
            // leaving trustManagers null accomplishes this
        }

        PRNGFixes.apply(); // fix Android SecureRandom issue on pre-KitKat platforms
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
    }
}
