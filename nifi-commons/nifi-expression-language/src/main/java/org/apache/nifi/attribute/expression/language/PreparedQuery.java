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
package org.apache.nifi.attribute.expression.language;

import org.apache.nifi.expression.AttributeValueDecorator;
import org.apache.nifi.processor.exception.ProcessException;

import java.util.List;
import java.util.Set;

public interface PreparedQuery {

    String evaluateExpressions(EvaluationContext evaluationContext, AttributeValueDecorator decorator) throws ProcessException;

    boolean isExpressionLanguagePresent();

    /**
     * Returns a {@link VariableImpact} that can be used to determine whether or not a given
     * variable impacts this Expression.
     *
     * @return a {@link VariableImpact} that can be used to determine whether or not a given
     *         variable impacts this Expression.
     */
    VariableImpact getVariableImpact();

    /**
     * Returns a Set of all attributes that are explicitly referenced by the Prepared Query.
     * There are some expressions, however, such as <code>${allMatchingAttributes('a.*'):gt(4)}</code>
     * that reference multiple attributes, but those attributes' names cannot be determined a priori. As a result,
     * those attributes will not be included in the returned set.
     *
     * @return a Set of all attributes that are explicitly referenced by the Prepared Query
     */
    Set<String> getExplicitlyReferencedAttributes();

    /**
     * @return the list of all Expressions that are used to make up the Prepared Query
     */
    List<Expression> getExpressions();
}
