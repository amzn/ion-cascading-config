// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import lombok.AllArgsConstructor;

/**
 * Represents a struct of IonProperties.
 */
@AllArgsConstructor
class DynamicIonStruct implements IonProperty {

    private final List<MatchableProperty> matchableProperties;

    @Override
    public IonValue getIonValue(final Predicate<Map.Entry<String, Set<String>>> condition) {
        final Map<String, IonProperty> aggregatedValues = IonConfigManager.cascadeMatchableProperties(matchableProperties, condition);
        final IonStruct result = IonConfigManager.ION_SYSTEM.newEmptyStruct();
        aggregatedValues.forEach((fieldName, property) -> result.add(fieldName, property.getIonValue(condition).clone()));
        return result;
    }

    @Override
    public boolean isListBased() {
        return false;
    }
}
