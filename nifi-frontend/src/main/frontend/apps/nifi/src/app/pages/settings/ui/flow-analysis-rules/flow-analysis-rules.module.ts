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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FlowAnalysisRules } from './flow-analysis-rules.component';
import { FlowAnalysisRuleTable } from './flow-analysis-rule-table/flow-analysis-rule-table.component';
import { NgxSkeletonLoaderModule } from 'ngx-skeleton-loader';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { NifiTooltipDirective } from '@nifi/shared';
import { PropertyTable } from '../../../../ui/common/property-table/property-table.component';
import { MatButtonModule } from '@angular/material/button';

@NgModule({
    declarations: [FlowAnalysisRules],
    exports: [FlowAnalysisRules],
    imports: [
        CommonModule,
        NgxSkeletonLoaderModule,
        MatSortModule,
        MatTableModule,
        NifiTooltipDirective,
        FlowAnalysisRuleTable,
        PropertyTable,
        MatButtonModule
    ]
})
export class FlowAnalysisRulesModule {}
