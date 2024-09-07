// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import lombok.Value;

/**
 * Represents a criterion as defined in config. For example:
 *
 * <pre>{@code
 *  'color-blue': {
 *      ...
 *  }
 * }</pre>
 * <p>
 * Would have a CriterionDefinition of
 * <pre>{@code
 * {
 *     identifier: {
 *         name: "color",
 *         isNegated: false
 *     },
 *     value: "blue"
 * }
 * }</pre>
 */
@Value
class CriterionDefinition {

    private static final String CRITERIA_DEFINITION_DELIMITER = "-";

    private final CriterionIdentifier identifier;
    private final String value;

    static CriterionDefinition parse(final String criterionString) {
        final int delimiterIndex = criterionString.indexOf(CRITERIA_DEFINITION_DELIMITER);
        if (delimiterIndex < 1 || delimiterIndex >= criterionString.length() - 1) {
            // this is not a valid criteria definition, the delimiter must exist and cannot be at either end of the string
            return null;
        }

        final String value = criterionString.substring(delimiterIndex + 1);

        /*
         * Determine if criterion has a "not" condition. For example:
         *
         * Not negated:
         * 'color-blue': {
         *     ...
         * }
         *
         * Negated:
         * '!color-blue': {
         *     ...
         * }
         */
        final CriterionIdentifier identifier = criterionString.startsWith("!")
                ? new CriterionIdentifier(criterionString.substring(1, delimiterIndex), true)
                : new CriterionIdentifier(criterionString.substring(0, delimiterIndex), false);

        return new CriterionDefinition(identifier, value);
    }
}
