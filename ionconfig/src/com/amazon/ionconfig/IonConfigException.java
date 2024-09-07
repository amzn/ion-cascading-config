// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

public class IonConfigException extends RuntimeException {

    public IonConfigException(final String format, final Object... formatArgs) {
        super(String.format(format, formatArgs));
    }

    public IonConfigException(final Throwable cause, final String format, final Object... formatArgs) {
        super(String.format(format, formatArgs), cause);
    }
}
