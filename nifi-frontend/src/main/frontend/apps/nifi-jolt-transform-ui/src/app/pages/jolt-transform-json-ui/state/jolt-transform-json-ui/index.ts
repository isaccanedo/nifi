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

import { HttpErrorResponse } from '@angular/common/http';

export interface JoltTransformJsonUiState {
    saving: boolean;
    transformingJoltSpec: boolean;
    validatingJoltSpec: boolean | null;
    savingProperties: boolean;
    loadedTimestamp: string;
    status: 'pending' | 'loading' | 'success';
    savePropertiesResponse?: SavePropertiesSuccess | null;
    savePropertiesFailureResponse?: HttpErrorResponse | null;
    validationResponse?: ValidateJoltSpecSuccess | null;
    validationFailureResponse?: HttpErrorResponse | null;
    transformationResponse?: TransformJoltSpecSuccess | null;
    transformationFailureResponse?: HttpErrorResponse | null;
    processorDetails: ProcessorDetails | null;
}

export interface ValidateJoltSpecRequest {
    customClass: string;
    expressionLanguageAttributes: any;
    input: string;
    modules: string;
    specification: string;
    transform: string;
}

export interface SavePropertiesRequest {
    'Jolt Specification': string;
    'Jolt Transform': string;
    processorId: string;
    revision: string;
    clientId: string;
    disconnectedNodeAcknowledged: boolean;
}

export interface SavePropertiesSuccess {}

export interface ValidateJoltSpecSuccess {
    valid: boolean;
    message: string | null;
}

export interface TransformJoltSpecSuccess {}

export interface ProcessorDetails {
    id: string;
    descriptors: {
        [key: string]: any;
    };
    properties: any;
    name: string;
    state: string;
    type: string;
    validationErrors: [] | null;
    annotationData: [] | null;
}
