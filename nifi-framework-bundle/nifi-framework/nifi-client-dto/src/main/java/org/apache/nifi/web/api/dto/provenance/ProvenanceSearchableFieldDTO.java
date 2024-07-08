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
package org.apache.nifi.web.api.dto.provenance;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlType;

/**
 * A searchable field for provenance queries.
 */
@XmlType(name = "provenanceSearchableField")
public class ProvenanceSearchableFieldDTO {

    private String id;
    private String field;
    private String label;
    private String type;

    /**
     * @return id of this searchable field
     */
    @Schema(description = "The id of the searchable field."
    )
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the field
     */
    @Schema(description = "The searchable field."
    )
    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    /**
     * @return label for this field
     */
    @Schema(description = "The label for the searchable field."
    )
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @return type of this field
     */
    @Schema(description = "The type of the searchable field."
    )
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
