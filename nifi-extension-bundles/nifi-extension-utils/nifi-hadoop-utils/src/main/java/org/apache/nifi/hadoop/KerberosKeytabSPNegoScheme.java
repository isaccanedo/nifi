/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.hadoop;

import org.apache.hadoop.security.authentication.util.KerberosUtil;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.auth.SPNegoScheme;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * This class provides a very similar authentication scheme and token generation as {@link SPNegoScheme} does.
 * The token generation is based on a keytab file coming from {@link KerberosKeytabCredentials} and the process
 * uses hadoop-auth tools.
 */
public class KerberosKeytabSPNegoScheme extends SPNegoScheme {

    public KerberosKeytabSPNegoScheme() {
        super(true, false);
    }

    @Override
    public byte[] generateToken(byte[] input, String authServer, Credentials credentials) {
        Set<Principal> principals = new HashSet<>();
        principals.add(credentials.getUserPrincipal());
        Subject subject = new Subject(false, principals, new HashSet<>(), new HashSet<>());

        try {
            LoginContext loginContext = new LoginContext("", subject, null,
                new KerberosConfiguration(credentials.getUserPrincipal().getName(),
                    ((KerberosKeytabCredentials) credentials).getKeytab()));
            loginContext.login();
            Subject loggedInSubject = loginContext.getSubject();

            return Subject.callAs(loggedInSubject, new Callable<byte[]>() {

                public byte[] call() throws UnknownHostException, GSSException {
                    final GSSManager gssManager = GSSManager.getInstance();
                    final String servicePrincipal = KerberosUtil.getServicePrincipal("HTTP", authServer);
                    final GSSName serviceName = gssManager.createName(servicePrincipal, KerberosUtil.NT_GSS_KRB5_PRINCIPAL_OID);
                    final GSSContext gssContext = gssManager.createContext(serviceName, KerberosUtil.GSS_KRB5_MECH_OID, null, 0);
                    gssContext.requestCredDeleg(true);
                    gssContext.requestMutualAuth(true);
                    return gssContext.initSecContext(input, 0, input.length);
                }

            });
        } catch (final LoginException e) {
            throw new RuntimeException(e);
        }
    }

}
