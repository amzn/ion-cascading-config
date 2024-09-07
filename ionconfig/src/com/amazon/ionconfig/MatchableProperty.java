// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import java.util.List;
import java.util.Map;

import lombok.Value;

/**
 * A MatchableProperty represents a list of values that should be applied if a list of criteria are passed. For
 * example:
 *
 * <pre>{@code
 * 'family-brass': {
 *     'size-large': {
 *         instrument: "tuba"
 *     }
 * }
 * }</pre>
 * <p>
 * The resulting MatchableProperty would be:
 * <pre>{@code
 * {
 *     criteria: [
 *         {
 *             identifier: {
 *                 name: "family",
 *                 isNegated: false
 *             },
 *             values: ["brass"]
 *         },
 *         {
 *             identifier: {
 *                 name: "size",
 *                 isNegated: false
 *             },
 *             values: ["large"]
 *         }
 *     ],
 *     values: {
 *         instrument: "tuba"
 *     }
 * }
 * }</pre>
 * <p>
 * As the Ion config is parsed, these properties are assembled into a list ordered from least to most important.
 * Then when values are queried, this list is iterated over and values are added as they match the criteria,
 * overwriting old values with more important ones.
 */
@Value
class MatchableProperty {

    private final List<GroupedCriteriaDefinition> criteria;
    private final Map<String, IonProperty> values;
}
