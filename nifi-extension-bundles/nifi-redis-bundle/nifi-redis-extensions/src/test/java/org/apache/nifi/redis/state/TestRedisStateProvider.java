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
package org.apache.nifi.redis.state;

import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.state.StateProviderInitializationContext;
import org.apache.nifi.redis.util.RedisUtils;
import org.apache.nifi.util.MockPropertyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLContext;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRedisStateProvider {

    private RedisStateProvider redisStateProvider;
    private StateProviderInitializationContext context;
    private ValidationContext validationContext;

    @BeforeEach
    public void init() {
        context = Mockito.mock(StateProviderInitializationContext.class);
        redisStateProvider = new RedisStateProvider();
        validationContext = Mockito.mock(ValidationContext.class);

        // Set up mock state provider init context
        Mockito.when(context.getProperty(RedisStateProvider.KEY_PREFIX)).thenReturn(new MockPropertyValue("/nifi/components/"));
        Mockito.when(context.getProperty(RedisStateProvider.MAX_ATTEMPTS)).thenReturn(new MockPropertyValue("20"));

        // Set up mock validation context
        Mockito.when(validationContext.getProperty(RedisUtils.CONNECTION_STRING)).thenReturn(new MockPropertyValue("localhost:6379"));
        Mockito.when(validationContext.getProperty(RedisUtils.REDIS_MODE)).thenReturn(new MockPropertyValue("Standalone"));
        Mockito.when(validationContext.getProperty(RedisUtils.DATABASE)).thenReturn(new MockPropertyValue("0"));
    }

    private void enableTls(boolean enable) {
        Mockito.when(validationContext.getProperty(RedisStateProvider.ENABLE_TLS)).thenReturn(new MockPropertyValue(String.valueOf(enable)));
        Mockito.when(context.getProperty(RedisStateProvider.ENABLE_TLS)).thenReturn(new MockPropertyValue(String.valueOf(enable)));

        if (enable) {
            SSLContext sslContext = Mockito.mock(SSLContext.class);
            Mockito.when(context.getSSLContext()).thenReturn(sslContext);
        }
    }

    @Test
    public void customValidate_enabledTlsSuccess() {
        this.enableTls(true);

        redisStateProvider.initialize(context);

        Collection<ValidationResult> results = redisStateProvider.customValidate(validationContext);
        assertTrue(results.isEmpty());
    }

    @Test
    public void customValidate_disableTlsSuccess() {
        this.enableTls(false);

        redisStateProvider.initialize(context);

        Collection<ValidationResult> results = redisStateProvider.customValidate(validationContext);
        assertTrue(results.isEmpty());
    }

    @Test
    public void customValidate_enableTlsButNoSslContext() {
        this.enableTls(true);

        Mockito.when(context.getSSLContext()).thenReturn(null);

        redisStateProvider.initialize(context);

        Collection<ValidationResult> results = redisStateProvider.customValidate(validationContext);
        assertEquals(1, results.size());
    }
}
