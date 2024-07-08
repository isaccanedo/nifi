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
package org.apache.nifi.web.api.dto.action;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.util.TimeAdapter;
import org.apache.nifi.web.api.entity.ActionEntity;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Collection;
import java.util.Date;

/**
 * NiFi action history.
 */
@XmlType(name = "history")
public class HistoryDTO {

    private Integer total;
    private Date lastRefreshed;
    private Collection<ActionEntity> actions;

    /**
     * @return total number of actions
     */
    @Schema(description = "The number of number of actions that matched the search criteria.."
    )
    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    /**
     * @return timestamp when these records were returned
     */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    @Schema(description = "The timestamp when the report was generated.",
            type = "string"
    )
    public Date getLastRefreshed() {
        return lastRefreshed;
    }

    public void setLastRefreshed(Date lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
    }

    /**
     * @return actions for this range
     */
    @Schema(description = "The actions."
    )
    public Collection<ActionEntity> getActions() {
        return actions;
    }

    public void setActions(Collection<ActionEntity> actions) {
        this.actions = actions;
    }
}
