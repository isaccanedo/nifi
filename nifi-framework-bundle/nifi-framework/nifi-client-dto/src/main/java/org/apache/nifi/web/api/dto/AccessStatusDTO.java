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
package org.apache.nifi.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * A serialized representation of this class can be placed in the entity body of a response to the API. This particular entity holds the users access status.
 */
@XmlRootElement(name = "accessStatus")
public class AccessStatusDTO {

    public static enum Status {

        UNKNOWN,
        ACTIVE
    }

    private String identity;
    private String username;
    private String status;
    private String message;

    /**
     * @return the user identity
     */
    @Schema(description = "The user identity.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    /**
     * @return the user access status
     */
    @Schema(description = "The user access status.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return additional details about the user access status
     */
    @Schema(description = "Additional details about the user access status.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
