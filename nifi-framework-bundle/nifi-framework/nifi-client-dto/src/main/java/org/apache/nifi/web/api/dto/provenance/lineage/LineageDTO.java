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
package org.apache.nifi.web.api.dto.provenance.lineage;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.util.TimestampAdapter;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;

/**
 * Represents the lineage for a flowfile.
 */
@XmlType(name = "lineage")
public class LineageDTO {

    private String id;
    private String uri;

    private Date submissionTime;
    private Date expiration;
    private Integer percentCompleted;
    private Boolean finished;

    private LineageRequestDTO request;
    private LineageResultsDTO results;

    /**
     * @return id of this lineage
     */
    @Schema(description = "The id of this lineage query."
    )
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return uri for this lineage
     */
    @Schema(description = "The URI for this lineage query for later retrieval and deletion."
    )
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return submission time for this lineage
     */
    @XmlJavaTypeAdapter(TimestampAdapter.class)
    @Schema(description = "When the lineage query was submitted.",
            type = "string"
    )
    public Date getSubmissionTime() {
        return submissionTime;
    }

    public void setSubmissionTime(Date submissionTime) {
        this.submissionTime = submissionTime;
    }

    /**
     * @return expiration of this lineage
     */
    @XmlJavaTypeAdapter(TimestampAdapter.class)
    @Schema(description = "When the lineage query will expire.",
            type = "string"
    )
    public Date getExpiration() {
        return expiration;
    }

    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    /**
     * @return percent completed for this result
     */
    @Schema(description = "The percent complete for the lineage query."
    )
    public Integer getPercentCompleted() {
        return percentCompleted;
    }

    public void setPercentCompleted(Integer percentCompleted) {
        this.percentCompleted = percentCompleted;
    }

    /**
     * @return whether or not the request is finished running
     */
    @Schema(description = "Whether the lineage query has finished."
    )
    public Boolean getFinished() {
        return finished;
    }

    public void setFinished(Boolean finished) {
        this.finished = finished;
    }

    /**
     * @return the lineage request
     */
    @Schema(description = "The initial lineage result."
    )
    public LineageRequestDTO getRequest() {
        return request;
    }

    public void setRequest(LineageRequestDTO request) {
        this.request = request;
    }

    /**
     * @return the results of this lineage
     */
    @Schema(description = "The results of the lineage query."
    )
    public LineageResultsDTO getResults() {
        return results;
    }

    public void setResults(LineageResultsDTO results) {
        this.results = results;
    }

}
