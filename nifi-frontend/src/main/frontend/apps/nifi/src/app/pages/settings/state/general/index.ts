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

import { Revision } from '../../../../state/shared';

export const generalFeatureKey = 'general';

export interface ControllerConfigResponse {
    controller: ControllerEntity;
}

export interface UpdateControllerConfigRequest {
    controller: ControllerEntity;
}

export interface Controller {
    maxTimerDrivenThreadCount: number;
}

export interface ControllerEntity {
    revision: Revision;
    disconnectedNodeAcknowledged?: boolean;
    component: Controller;
}

export interface GeneralState {
    controller: ControllerEntity;
    saving: boolean;
    status: 'pending' | 'loading' | 'success';
}
