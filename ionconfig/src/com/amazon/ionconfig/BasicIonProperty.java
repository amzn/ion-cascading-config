// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import lombok.AllArgsConstructor;

/**
 * Represents primitive type IonProperties. I.E. Strings, Symbols, Numbers
 */
@AllArgsConstructor
class BasicIonProperty implements IonProperty {

    private final IonValue ionValue;

    @Override
    public IonValue getIonValue(final Predicate<Map.Entry<String, Set<String>>> condition) {
        return ionValue;
    }

    @Override
    public boolean isListBased() {
        return !ionValue.isNullValue() && ionValue.getType() == IonType.LIST;
    }
}
