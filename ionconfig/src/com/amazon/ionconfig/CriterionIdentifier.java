// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import lombok.Value;

/**
 * Identifies the type of a Criterion in config. For example:
 * <pre>{@code
 *  'color-blue': {
 *      ...
 *  }
 * }</pre>
 * <p>
 * Would have a CriterionIdentifier of
 * <pre>{@code
 * {
 *   name: "color",
 *   isNegated: false
 * }
 * }</pre>
 */
@Value
class CriterionIdentifier {

    private final String name;
    private final boolean isNegated;
}
