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
package org.apache.nifi.security.krb;

import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.exception.ProcessException;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;

/**
 * Helper class for processors to perform an action as a KerberosUser.
 */
public class KerberosAction<T> {

    private final KerberosUser kerberosUser;
    private final PrivilegedExceptionAction<T> action;
    private final ComponentLog logger;
    private final ClassLoader contextClassLoader;

    public KerberosAction(final KerberosUser kerberosUser,
                          final PrivilegedExceptionAction<T> action,
                          final ComponentLog logger) {
        this(kerberosUser, action, logger, null);
    }

    public KerberosAction(final KerberosUser kerberosUser,
                          final PrivilegedExceptionAction<T> action,
                          final ComponentLog logger,
                          final ClassLoader contextClassLoader) {
        this.kerberosUser = Objects.requireNonNull(kerberosUser);
        this.action = Objects.requireNonNull(action);
        this.logger = Objects.requireNonNull(logger);
        this.contextClassLoader = contextClassLoader;
    }

    public T execute() {
        T result;
        // lazily login the first time the processor executes
        if (!kerberosUser.isLoggedIn()) {
            try {
                kerberosUser.login();
                logger.info("Successful login for {}", kerberosUser.getPrincipal());
            } catch (final KerberosLoginException e) {
                throw new ProcessException("Login failed due to: " + e.getMessage(), e);
            }
        }

        // check if we need to re-login, will only happen if re-login window is reached (80% of TGT life)
        try {
            kerberosUser.checkTGTAndRelogin();
        } catch (final KerberosLoginException e) {
            throw new ProcessException("Relogin check failed due to: " + e.getMessage(), e);
        }

        // attempt to execute the action, if an exception is caught attempt to logout/login and retry
        try {
            if (contextClassLoader == null) {
                result = kerberosUser.doAs(action);
            } else {
                result = kerberosUser.doAs(action, contextClassLoader);
            }
        } catch (final SecurityException se) {
            logger.info("Privileged action failed, attempting relogin and retrying...");
            logger.debug("", se);

            try {
                kerberosUser.logout();
                kerberosUser.login();
                result = kerberosUser.doAs(action);
            } catch (Exception e) {
                throw new ProcessException("Retrying privileged action failed due to: " + e.getMessage(), e);
            }
        } catch (final PrivilegedActionException pae) {
            final Exception cause = pae.getException();
            throw new ProcessException("Privileged action failed due to: " + cause.getMessage(), cause);
        }

        return result;
    }

}
