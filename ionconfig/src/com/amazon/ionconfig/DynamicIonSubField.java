// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonList;
import com.amazon.ion.IonValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;

/**
 * Represents an element of a list of IonProperties.
 */
@AllArgsConstructor
class DynamicIonSubField implements IonProperty {

    private final List<MatchableProperty> subFieldProperties;

    @Override
    public IonValue getIonValue(final Predicate<Map.Entry<String, Set<String>>> condition) {
        // Technically, a sub field of a list could be a list but this would be invalid with the config specification and we do not support it.
        throw new UnsupportedOperationException("getIonValue is not supported for " + DynamicIonSubField.class.getCanonicalName());
    }

    /**
     * A List's sub field will either be a single field called "value" or a list called "values". We stream them so
     * that they are inlined into the parent list.
     */
    @Override
    public Stream<IonValue> getIonValues(final Predicate<Map.Entry<String, Set<String>>> condition) {
        /*
        If subFieldProperties has more than one element, it means that there was a list element conditioned by an OR

        Example:
        [
          'field1-true'::
          'field2-true'::{
            value: 1
          }
        ]

        when parsed this produces multiple matchable properties but all of them will have the same value. We don't want to
        use them all and return a stream of them all, otherwise there'd be a duplicate for every OR condition that passed.
        Instead, we should just sequentially test them all and use the first one that passes since they'll all be equivalent
        and the user only wants the value once.
         */
        final MatchableProperty matchedProperty = subFieldProperties.stream()
                .filter(property -> property.getCriteria().stream().allMatch(criteriaDefinition -> criteriaDefinition.testCondition(condition)))
                .findFirst()
                .orElse(null);

        if (matchedProperty != null) {
            final Map.Entry<String, IonProperty> entry = matchedProperty.getValues().entrySet().iterator().next();
            // stream "value"
            if (IonConfigManager.SUB_FIELD_VALUE_KEYWORD.equals(entry.getKey())) {
                return Stream.of(entry.getValue().getIonValue(condition));
            }

            // stream "values"
            return ((IonList) entry.getValue().getIonValue(condition)).stream();
        }

        // no match means stream nothing
        return Stream.empty();
    }

    @Override
    public boolean isListBased() {
        // Technically, a sub field of a list could be a list but this would be invalid with the config specification and we do not support it.
        throw new UnsupportedOperationException("isListBased is not supported for " + DynamicIonSubField.class.getCanonicalName());
    }
}
