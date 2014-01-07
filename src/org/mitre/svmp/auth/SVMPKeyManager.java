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
package org.mitre.svmp.auth;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyChain;
import android.security.KeyChainException;

import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author Joe Portner
 * Used for SSL client certificate authentication
 */
public class SVMPKeyManager implements X509KeyManager {
    private Context context;
    private String savedAlias;

    public SVMPKeyManager(Context context, String savedAlias) {
        this.context = context;
        this.savedAlias = savedAlias;
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        return savedAlias;
    }

    @Override
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
        return null; // not used
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public X509Certificate[] getCertificateChain(String s) {
        X509Certificate[] certificates = null;
        try {
            certificates = KeyChain.getCertificateChain(context, savedAlias);
        } catch (KeyChainException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (certificates == null)
            certificates = new X509Certificate[0];
        return certificates;
    }

    @Override
    public String[] getClientAliases(String s, Principal[] principals) {
        String[] clientAliases;
        if (savedAlias != null && savedAlias.length() > 0)
            clientAliases = new String[] {savedAlias};
        else
            clientAliases = new String[0];
        return clientAliases;
    }

    @Override
    public String[] getServerAliases(String s, Principal[] principals) {
        return new String[0]; // not used
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public PrivateKey getPrivateKey(String s) {
        PrivateKey privateKey = null;
        try {
            privateKey = KeyChain.getPrivateKey(context, savedAlias);
        } catch (KeyChainException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return privateKey;
    }
}
