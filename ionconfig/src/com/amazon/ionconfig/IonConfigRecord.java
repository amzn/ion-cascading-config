// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonValue;

import lombok.Builder;
import lombok.Value;

/**
 * Represents an input IonValue which should be used as part of the IonConfigManager. These records are expected to
 * be in the raw, unparsed IonCascadingConfig format and the IonConfigManager will parse them and then allow for
 * values to be accessed.
 */
@Builder(toBuilder = true)
@Value
public class IonConfigRecord {

    /**
     * The name of this record. It will be referred to in exception messages should any error occur while processing
     * the config. {@link Object#toString()} should be implemented for more useful exception messages. Allows for any
     * Object as the name so long as it has toString implemented. This allows for more complex request or query objects
     * to be used as the name here and referred to but their string value doesn't need to be fully computed unless there
     * is an error while parsing the record. Examples of the name could be a file name or a database key.
     */
    private final Object name;
    private final IonValue value;
}
