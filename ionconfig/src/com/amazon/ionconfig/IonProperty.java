// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonValue;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An IonProperty is an object that can produce an IonValue based on a custom predicate by checking if any
 * underlying MatchableProperty objects match it.
 */
interface IonProperty {

    IonValue getIonValue(Predicate<Map.Entry<String, Set<String>>> condition);

    default Stream<IonValue> getIonValues(Predicate<Map.Entry<String, Set<String>>> condition) {
        return Stream.of(getIonValue(condition));
    }

    boolean isListBased();
}
