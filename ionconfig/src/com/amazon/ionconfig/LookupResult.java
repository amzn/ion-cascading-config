// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonValue;

import java.util.Map;

import lombok.Value;

@Value
class LookupResult {

    private final Map<String, CriteriaPredicate> inputPredicates;
    private final Map<String, IonValue> outputValues;
}
