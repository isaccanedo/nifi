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

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComponentStateDialog } from './component-state.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideMockStore } from '@ngrx/store/testing';
import { initialState } from '../../../state/component-state/component-state.reducer';
import { MatDialogRef } from '@angular/material/dialog';

describe('ComponentStateDialog', () => {
    let component: ComponentStateDialog;
    let fixture: ComponentFixture<ComponentStateDialog>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ComponentStateDialog, NoopAnimationsModule],
            providers: [provideMockStore({ initialState }), { provide: MatDialogRef, useValue: null }]
        });
        fixture = TestBed.createComponent(ComponentStateDialog);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
