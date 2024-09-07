// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import lombok.Value;

/**
 * A collection of factory methods to create an instance of a CriteriaPredicate. A CriteriaPredicate created by any of
 * the factory methods is immutable and thread-safe unless the {@link CriteriaPredicate#fromCondition(Predicate)} is
 * used with a non-thread-safe Predicate.
 */
final class StandardPredicates {

    private StandardPredicates() {
    }

    /**
     * Checks if there are any intersecting elements between the two given sets. Implementing this class rather than a
     * lambda to create a nice toString.
     */
    @Value
    static final class IntersectsSet implements CriteriaPredicate {

        private final Set<String> valueSet;

        @Override
        public boolean test(final Set<String> criteriaValues) {
            return !Collections.disjoint(valueSet, criteriaValues);
        }
    }

    /**
     * Checks if the criteriaValues contain the value. Implementing this class rather than a lambda to create a nice
     * toString.
     */
    @Value
    static final class ContainsString implements CriteriaPredicate {

        private final String value;

        @Override
        public boolean test(final Set<String> criteriaValues) {
            return criteriaValues.contains(value);
        }
    }
}
