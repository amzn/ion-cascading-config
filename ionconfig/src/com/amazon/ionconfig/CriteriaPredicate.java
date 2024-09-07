// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Used in the {@link IonConfigManager#getValuesForPredicates(String, Map)} method allowing callers to create custom
 * logic to check if a criteria passes when processing IonCascadingConfig.
 */
@FunctionalInterface
public interface CriteriaPredicate extends Predicate<Set<String>> {

    /**
     * A criteria predicate that always returns false.
     */
    CriteriaPredicate ALWAYS_FALSE = criteriaValues -> false;

    /**
     * Tests if the given criteria values pass the predicate's condition.
     *
     * @param criteriaValues A Set of criteria values from config, non-null.
     * @return True if these values pass the predicate.
     */
    boolean test(Set<String> criteriaValues);

    /**
     * A convenience CriteriaPredicate factory that checks if the Set of criteria values are contained within the given
     * values.
     *
     * @param values A series of values to check against.
     * @return True if any of the criteria values are contained in the given values.
     */
    static CriteriaPredicate fromValues(final String... values) {
        if (values.length == 1) {
            return fromValue(values[0]);
        }

        final Set<String> valueSet = values.length == 0
                ? Collections.emptySet()
                : Collections.unmodifiableSet(Arrays.stream(values).collect(Collectors.toSet()));

        return new StandardPredicates.IntersectsSet(valueSet);
    }

    /**
     * A convenience CriteriaPredicate factory that checks if the Set of criteria values are contained within the given
     * Set.
     *
     * @param values A Set of Strings to check against.
     * @return True if any of the criteria values are contained in the given Set.
     * @throws NullPointerException If the given Set is null.
     */
    static CriteriaPredicate fromValues(final Set<String> values) {
        return fromValues(Objects.requireNonNull(values, "'values' may not be null.").toArray(new String[0]));
    }

    /**
     * A convenience CriteriaPredicate factory that checks if the given string is contained within the Set of criteria
     * values.
     *
     * @param value A String to check against.
     * @return True if any of the criteria values equal the given String.
     */
    static CriteriaPredicate fromValue(final String value) {
        return new StandardPredicates.ContainsString(value);
    }

    /**
     * A convenience CriteriaPredicate factory. Using the given predicate, this creates a criteria predicate which
     * checks if any String in the Set of criteria equal it.
     *
     * @param condition A predicate which acts on a single string instead of the entire Set.
     * @return True if any of the criteria values pass the given predicate.
     */
    static CriteriaPredicate fromCondition(final Predicate<String> condition) {
        return criteriaValues -> criteriaValues.stream().anyMatch(condition);
    }

    /**
     * Converts a Map of key value pairs into a Map of key to criteria predicates using the {@link #fromValue(String)}
     * factory.
     *
     * @param properties A Map of key value pairs.
     * @return A Map of key to criteria predicates.
     */
    static Map<String, CriteriaPredicate> convertStringMap(final Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyMap();
        }

        return properties.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> fromValue(entry.getValue())));
    }

    /**
     * Converts a Map of key to value set pairs into a Map of key to criteria predicates using the {@link
     * #fromValues(Set)} factory.
     *
     * @param properties A Map of key value pairs.
     * @return A Map of key to criteria predicates.
     */
    static Map<String, CriteriaPredicate> convertStringSetMap(final Map<String, Set<String>> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyMap();
        }

        return properties.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> fromValues(entry.getValue())));
    }

}
