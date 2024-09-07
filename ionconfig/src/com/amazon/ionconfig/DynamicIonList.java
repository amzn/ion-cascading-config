// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;

/**
 * Represents a list of IonProperties.
 */
@AllArgsConstructor
class DynamicIonList implements IonProperty {

    private final List<IonProperty> properties;

    @Override
    public IonValue getIonValue(final Predicate<Map.Entry<String, Set<String>>> condition) {
        return properties.stream()
                .flatMap(property -> property.getIonValues(condition))
                .map(IonValue::clone)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> IonConfigManager.ION_SYSTEM.newList(list.toArray(new IonValue[0]))));
    }

    @Override
    public boolean isListBased() {
        return true;
    }
}
