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

package org.apache.nifi.stateless.parameters;

import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.stateless.parameter.AbstractParameterValueProvider;

import java.util.Collection;
import java.util.Collections;

public class InvalidParameterValueProvider extends AbstractParameterValueProvider {

    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext validationContext) {
        final ValidationResult validationResult = new ValidationResult.Builder()
            .valid(false)
            .explanation("This Parameter Value Provider is never valid")
            .build();

        return Collections.singleton(validationResult);
    }

    @Override
    public String getParameterValue(final String contextName, final String parameterName) {
        return null;
    }

    @Override
    public boolean isParameterDefined(final String contextName, final String parameterName) {
        return false;
    }
}
