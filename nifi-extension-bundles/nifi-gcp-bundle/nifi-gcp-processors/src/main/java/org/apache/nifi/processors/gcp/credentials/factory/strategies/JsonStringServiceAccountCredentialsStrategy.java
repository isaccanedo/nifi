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
package org.apache.nifi.processors.gcp.credentials.factory.strategies;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processors.gcp.credentials.factory.CredentialPropertyDescriptors;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;


/**
 * Supports service account credentials provided as a JSON string.
 *
 * @see <a href="https://cloud.google.com/iam/docs/service-accounts">
 *     Service Accounts</a>
 */
public class JsonStringServiceAccountCredentialsStrategy extends AbstractServiceAccountCredentialsStrategy {

    public JsonStringServiceAccountCredentialsStrategy() {
        super("Service Account Credentials (Json String)", new PropertyDescriptor[] {
                CredentialPropertyDescriptors.SERVICE_ACCOUNT_JSON
        });
    }

    @Override
    protected InputStream getServiceAccountJson(Map<PropertyDescriptor, String> properties) {
        String serviceAccountJson = properties.get(CredentialPropertyDescriptors.SERVICE_ACCOUNT_JSON);
        return new ByteArrayInputStream(serviceAccountJson.getBytes());
    }
}
