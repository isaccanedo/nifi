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
package org.apache.nifi.web.spring;

import org.apache.nifi.controller.FlowController;
import org.apache.nifi.controller.flowanalysis.FlowAnalysisRuleProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class FlowAnalysisRuleProviderFactoryBean implements FactoryBean<FlowAnalysisRuleProvider>, ApplicationContextAware {

    private ApplicationContext context;
    private FlowAnalysisRuleProvider flowAnalysisRuleProvider;

    @Override
    public FlowAnalysisRuleProvider getObject() throws Exception {
        if (flowAnalysisRuleProvider == null) {
            flowAnalysisRuleProvider = context.getBean("flowController", FlowController.class);
        }

        return flowAnalysisRuleProvider;
    }

    @Override
    public Class<FlowAnalysisRuleProvider> getObjectType() {
        return FlowAnalysisRuleProvider.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = context;
    }
}
