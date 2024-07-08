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
import org.apache.nifi.web.api.dto.util.TimeAdapter;
import org.apache.nifi.web.api.entity.BulletinEntity;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;
import java.util.List;

/**
 * The contents for the bulletin board including the bulletins and the timestamp when the board was generated.
 */
@XmlType(name = "bulletinBoard")
public class BulletinBoardDTO {

    private List<BulletinEntity> bulletins;
    private Date generated;

    /**
     * @return bulletins to populate in the bulletin board
     */
    @Schema(description = "The bulletins in the bulletin board, that matches the supplied request."
    )
    public List<BulletinEntity> getBulletins() {
        return bulletins;
    }

    public void setBulletins(List<BulletinEntity> bulletins) {
        this.bulletins = bulletins;
    }

    /**
     * @return when this bulletin board was generated
     */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    @Schema(description = "The timestamp when this report was generated.",
            type = "string"
    )
    public Date getGenerated() {
        return generated;
    }

    public void setGenerated(final Date generated) {
        this.generated = generated;
    }

}
