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
package org.apache.nifi.authorization;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AuthorizationAuditorInvocationHandler implements InvocationHandler {

    private final Authorizer authorizer;
    private final AuthorizationAuditor auditor;
    private static final Method auditAccessAttemptMethod;

    static {
        try {
            auditAccessAttemptMethod = AuthorizationAuditor.class.getMethod("auditAccessAttempt", AuthorizationRequest.class, AuthorizationResult.class);
        } catch (final Exception e) {
            throw new RuntimeException("Unable to obtain necessary class information for AccessPolicyProvider", e);
        }
    }

    public AuthorizationAuditorInvocationHandler(final Authorizer authorizer, final AuthorizationAuditor auditor) {
        this.authorizer = authorizer;
        this.auditor = auditor;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            if (auditAccessAttemptMethod.equals(method)) {
                return method.invoke(auditor, args);
            } else {
                return method.invoke(authorizer, args);
            }
        } catch (final InvocationTargetException e) {
            // If the proxied instance throws an Exception, it'll be wrapped in an InvocationTargetException. We want
            // to instead re-throw what the proxied instance threw, so we pull it out of the InvocationTargetException.
            throw e.getCause();
        }
    }
}
