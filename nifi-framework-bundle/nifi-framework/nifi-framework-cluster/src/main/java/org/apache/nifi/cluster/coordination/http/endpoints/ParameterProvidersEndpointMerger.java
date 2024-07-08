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
package org.apache.nifi.cluster.coordination.http.endpoints;

import org.apache.nifi.cluster.coordination.http.EndpointResponseMerger;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.web.api.entity.ParameterProviderEntity;
import org.apache.nifi.web.api.entity.ParameterProvidersEntity;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ParameterProvidersEndpointMerger extends AbstractSingleEntityEndpoint<ParameterProvidersEntity> implements EndpointResponseMerger {
    private static final Pattern PARAMETER_PROVIDER_URI = Pattern.compile("/nifi-api/flow/parameter-providers");

    @Override
    public boolean canHandle(final URI uri, final String method) {
        return "GET".equalsIgnoreCase(method) && PARAMETER_PROVIDER_URI.matcher(uri.getPath()).matches();
    }

    @Override
    protected Class<ParameterProvidersEntity> getEntityClass() {
        return ParameterProvidersEntity.class;
    }

    @Override
    protected void mergeResponses(final ParameterProvidersEntity clientEntity, final Map<NodeIdentifier, ParameterProvidersEntity> entityMap, final Set<NodeResponse> successfulResponses,
                                  final Set<NodeResponse> problematicResponses) {

        final Map<String, ParameterProviderEntity> providerEntities = new HashMap<>();
        for (final ParameterProvidersEntity providersEntity : entityMap.values()) {
            for (final ParameterProviderEntity entity : providersEntity.getParameterProviders()) {
                final ParameterProviderEntity mergedEntity = providerEntities.get(entity.getId());
                if (mergedEntity == null) {
                    providerEntities.put(entity.getId(), entity);
                    continue;
                }

                ParameterProviderMerger.merge(mergedEntity, entity);
            }
        }

        clientEntity.setParameterProviders(new HashSet<>(providerEntities.values()));
        clientEntity.setCurrentTime(new Date());
    }

}
