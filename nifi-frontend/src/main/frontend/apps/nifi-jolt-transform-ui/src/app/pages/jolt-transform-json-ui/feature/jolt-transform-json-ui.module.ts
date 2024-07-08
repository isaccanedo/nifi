/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JoltTransformJsonUi } from './jolt-transform-json-ui.component';
import { JoltTransformJsonUiRoutingModule } from './jolt-transform-json-ui-routing.module';
import { StoreModule } from '@ngrx/store';
import { joltTransformJsonUiFeatureKey, reducers } from '../state';
import { EffectsModule } from '@ngrx/effects';
import { MatDialogModule } from '@angular/material/dialog';
import { JoltTransformJsonUiEffects } from '../state/jolt-transform-json-ui/jolt-transform-json-ui.effects';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CdkDrag } from '@angular/cdk/drag-drop';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatOption } from '@angular/material/autocomplete';
import { MatSelect } from '@angular/material/select';
import { NifiTooltipDirective, ComponentContext } from '@nifi/shared';
import { MatInput } from '@angular/material/input';
import { MatExpansionModule } from '@angular/material/expansion';

@NgModule({
    declarations: [JoltTransformJsonUi],
    exports: [JoltTransformJsonUi],
    imports: [
        CommonModule,
        JoltTransformJsonUiRoutingModule,
        StoreModule.forFeature(joltTransformJsonUiFeatureKey, reducers),
        EffectsModule.forFeature(JoltTransformJsonUiEffects),
        MatDialogModule,
        CodemirrorModule,
        ReactiveFormsModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        CdkDrag,
        MatFormField,
        MatLabel,
        MatOption,
        MatSelect,
        NifiTooltipDirective,
        MatInput,
        MatHint,
        ComponentContext,
        MatExpansionModule,
        FormsModule
    ]
})
export class JoltTransformJsonUiModule {}
