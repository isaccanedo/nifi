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
package org.apache.nifi.web.api.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * The results of a search on this NiFi.
 */
@XmlType(name = "searchResults")
public class SearchResultsDTO {

    private List<ComponentSearchResultDTO> processorResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> connectionResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> processGroupResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> inputPortResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> outputPortResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> remoteProcessGroupResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> funnelResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> labelResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> controllerServiceNodeResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> parameterContextResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> parameterProviderNodeResults = new ArrayList<>();
    private List<ComponentSearchResultDTO> parameterResults = new ArrayList<>();

    /**
     * @return The processors that matched the search
     */
    @Schema(description = "The processors that matched the search."
    )
    public List<ComponentSearchResultDTO> getProcessorResults() {
        return processorResults;
    }

    public void setProcessorResults(List<ComponentSearchResultDTO> processorResults) {
        this.processorResults = processorResults;
    }

    /**
     * @return connections that matched the search
     */
    @Schema(description = "The connections that matched the search."
    )
    public List<ComponentSearchResultDTO> getConnectionResults() {
        return connectionResults;
    }

    public void setConnectionResults(List<ComponentSearchResultDTO> connectionResults) {
        this.connectionResults = connectionResults;
    }

    /**
     * @return process group that matched the search
     */
    @Schema(description = "The process groups that matched the search."
    )
    public List<ComponentSearchResultDTO> getProcessGroupResults() {
        return processGroupResults;
    }

    public void setProcessGroupResults(List<ComponentSearchResultDTO> processGroupResults) {
        this.processGroupResults = processGroupResults;
    }

    /**
     * @return input ports that matched the search
     */
    @Schema(description = "The input ports that matched the search."
    )
    public List<ComponentSearchResultDTO> getInputPortResults() {
        return inputPortResults;
    }

    /**
     * @return output ports that matched the search
     */
    @Schema(description = "The output ports that matched the search."
    )
    public List<ComponentSearchResultDTO> getOutputPortResults() {
        return outputPortResults;
    }

    public void setInputPortResults(List<ComponentSearchResultDTO> inputPortResults) {
        this.inputPortResults = inputPortResults;
    }

    public void setOutputPortResults(List<ComponentSearchResultDTO> outputPortResults) {
        this.outputPortResults = outputPortResults;
    }

    /**
     * @return remote process groups that matched the search
     */
    @Schema(description = "The remote process groups that matched the search."
    )
    public List<ComponentSearchResultDTO> getRemoteProcessGroupResults() {
        return remoteProcessGroupResults;
    }

    public void setRemoteProcessGroupResults(List<ComponentSearchResultDTO> remoteProcessGroupResults) {
        this.remoteProcessGroupResults = remoteProcessGroupResults;
    }

    /**
     * @return funnels that matched the search
     */
    @Schema(description = "The funnels that matched the search."
    )
    public List<ComponentSearchResultDTO> getFunnelResults() {
        return funnelResults;
    }

    public void setFunnelResults(List<ComponentSearchResultDTO> funnelResults) {
        this.funnelResults = funnelResults;
    }

    /**
     * @return labels that matched the search
     */
    @Schema(description = "The labels that matched the search."
    )
    public List<ComponentSearchResultDTO> getLabelResults() {
        return labelResults;
    }

    public void setLabelResults(List<ComponentSearchResultDTO> labelResults) {
        this.labelResults = labelResults;
    }

    /**
     * @return the controller service nodes that matched the search
     */
    @Schema(description = "The controller service nodes that matched the search"
    )
    public List<ComponentSearchResultDTO> getControllerServiceNodeResults() {
        return controllerServiceNodeResults;
    }

    public void setControllerServiceNodeResults(List<ComponentSearchResultDTO> controllerServiceNodeResults) {
        this.controllerServiceNodeResults = controllerServiceNodeResults;
    }

    /**
     * @return the parameter provider nodes that matched the search
     */
    @Schema(description = "The parameter provider nodes that matched the search"
    )
    public List<ComponentSearchResultDTO> getParameterProviderNodeResults() {
        return parameterProviderNodeResults;
    }

    public void setParameterProviderNodeResults(List<ComponentSearchResultDTO> parameterProviderNodeResults) {
        this.parameterProviderNodeResults = parameterProviderNodeResults;
    }

    /**
     * @return parameter contexts that matched the search.
     */
    @Schema(description = "The parameter contexts that matched the search."
    )
    public List<ComponentSearchResultDTO> getParameterContextResults() {
        return parameterContextResults;
    }

    public void setParameterContextResults(List<ComponentSearchResultDTO> parameterContextResults) {
        this.parameterContextResults = parameterContextResults;
    }

    /**
     * @return parameters that matched the search.
     */
    @Schema(description = "The parameters that matched the search."
    )
    public List<ComponentSearchResultDTO> getParameterResults() {
        return parameterResults;
    }

    public void setParameterResults(List<ComponentSearchResultDTO> parameterResults) {
        this.parameterResults = parameterResults;
    }
}
