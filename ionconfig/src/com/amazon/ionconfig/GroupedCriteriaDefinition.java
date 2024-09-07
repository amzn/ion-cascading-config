// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import lombok.Value;

/**
 * Represents an OR'd grouping of criteria. For example:
 *
 * <pre>{@code
 *  'color-blue': 'color-red'::{
 *      ...
 *  }
 * }</pre>
 * <p>
 * Would have a GroupedCriteriaDefinition of
 * <pre>{@code
 * {
 *     identifier: {
 *         name: "color",
 *         isNegated: false
 *     },
 *     values: ["blue", "red"]
 * }
 * }</pre>
 */
@Value
class GroupedCriteriaDefinition {

    private final CriterionIdentifier identifier;
    private final Set<String> values;

    String getName() {
        return identifier.getName();
    }

    boolean testCondition(final Predicate<Map.Entry<String, Set<String>>> input) {
        final Map.Entry<String, Set<String>> entry = new AbstractMap.SimpleImmutableEntry<>(getName(), values);
        final Predicate<Map.Entry<String, Set<String>>> condition = identifier.isNegated() ? input.negate() : input;
        return condition.test(entry);
    }
}
