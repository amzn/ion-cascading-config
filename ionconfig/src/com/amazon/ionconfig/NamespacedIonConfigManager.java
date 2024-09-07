// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * This class wraps the basic IonConfigManager and provides greater functionality and convenience. Initialize this class
 * with a namespace and optionally with default properties. They will be used for every attempt to find values so
 * callers do not have to specify it every time.
 * <br><br>
 * <p>
 * This class also allows callers to search for primitive values and objects from the Ion config by using the {@link
 * Query} factory methods such as {@link #asString()}, {@link #asInteger()}, and {@link #asClass(Class)}. Using
 * Finders:
 * </p>
 * <ul>
 * <li>Any value may be searched for directly by its key.</li>
 * <li>Additional properties can be specified as well, which will be added to the default properties that the
 * NamespacedIonConfigManager was initialized with, if any. If the same properties are specified as those that this
 * class was initialized with, the initial properties will be overwritten by the new ones for that call.</li>
 * <li>Values may be returned wrapped in an {@link Optional} by using {@link Query#find(String)} which will wrap the
 * value or return an empty optional if the value was not found or was null and the type doesn't have a custom null
 * value.</li>
 * <li>Values may be returned directly by calling {@link Query#findOrThrow(String)} which returns the value or throws
 * an {@link IllegalStateException} if it was not found or was null.</li>
 * <li>Any class that can be deserialized from Ion may be searched for as well by using the set of methods. To do this,
 * this class uses {@link IonObjectMapper} and the parameterized class must be passed to the call. If the
 * class to be searched for is a generic class, a {@link TypeReference} must be passed in instead of the Class. This
 * allows callers to search for Collection classes such as <pre>{@code Map<String, String>}</pre> or <pre>{@code
 * Map<String, Map<Long, List<Date>>>}</pre> and they will be properly deserialized if possible. In case there are
 * classes with custom deserialization requirements, a custom IonObjectMapper may be provided to this class during
 * construction, otherwise this class will use a default shared instance.</li>
 * </ul>
 * <p>See this package's Readme.md and unit tests for sample usage.</p>
 * <p>
 * This class is thread-safe and immutable.
 */
public class NamespacedIonConfigManager {

    /**
     * Using the LazyHolder pattern to ensure Object Mapper is not recreated for every new instance.
     */
    private static class LazyHolder {

        private static final IonObjectMapper OBJECT_MAPPER = createIonMapper();

        private static IonObjectMapper createIonMapper() {
            final IonObjectMapper objectMapper = new IonObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper;
        }
    }

    @Builder(toBuilder = true)
    @Getter
    public static class Options {

        private final String namespace;
        @Singular private final Map<String, String> defaultProperties;
        @Singular private final Map<String, CriteriaPredicate> defaultPredicates;
        private final IonConfigManager configManager;
        private final IonObjectMapper ionMapper;
        private final boolean queriesCacheResults;
    }

    @Getter private final String namespace;
    @Getter private final Map<String, String> defaultProperties;
    @Getter private final Map<String, CriteriaPredicate> defaultPredicates;
    private final IonConfigManager globalConfigManager;
    private final Map<String, IonValue> defaultValues;
    private final IonObjectMapper ionMapper;
    private final boolean queriesCacheResults;

    public NamespacedIonConfigManager(final String namespace) {
        this(namespace, Collections.emptyMap());
    }

    public NamespacedIonConfigManager(final String namespace, final Map<String, String> defaultProperties) {
        this(namespace, defaultProperties, null);
    }

    public NamespacedIonConfigManager(final String namespace, final Map<String, String> defaultProperties, final IonObjectMapper ionMapper) {
        this(Options.builder()
                .namespace(namespace)
                .defaultProperties(defaultProperties)
                .ionMapper(ionMapper)
                .build());
    }

    public NamespacedIonConfigManager(final Options options) {
        this.namespace = Objects.requireNonNull(options.namespace, "Namespace cannot be null.");
        this.defaultProperties = Optional.ofNullable(options.defaultProperties)
                .map(properties -> Collections.unmodifiableMap(new HashMap<>(properties)))
                .orElse(Collections.emptyMap());
        this.defaultPredicates = Optional.ofNullable(options.defaultPredicates)
                .map(inputPredicates -> {
                    final Map<String, CriteriaPredicate> fullPredicates = new HashMap<>(inputPredicates);
                    fullPredicates.putAll(CriteriaPredicate.convertStringMap(this.defaultProperties));
                    return Collections.unmodifiableMap(fullPredicates);
                })
                .orElse(Collections.emptyMap());
        this.globalConfigManager = Optional.ofNullable(options.configManager).orElseGet(IonConfigManager::getInstance);
        this.ionMapper = Optional.ofNullable(options.ionMapper).orElseGet(() -> LazyHolder.OBJECT_MAPPER);
        this.defaultValues = Collections.unmodifiableMap(this.globalConfigManager.getValuesForPredicates(this.namespace, this.defaultPredicates));
        this.queriesCacheResults = options.queriesCacheResults;
    }

    public Query<String> asString() {
        return newQuery().asString();
    }

    public Query<Integer> asInteger() {
        return newQuery().asInteger();
    }

    public Query<Long> asLong() {
        return newQuery().asLong();
    }

    public Query<BigInteger> asBigInteger() {
        return newQuery().asBigInteger();
    }

    public Query<BigDecimal> asBigDecimal() {
        return newQuery().asBigDecimal();
    }

    public Query<Double> asDouble() {
        return newQuery().asDouble();
    }

    public Query<Boolean> asBoolean() {
        return newQuery().asBoolean();
    }

    public Query<Date> asDate() {
        return newQuery().asDate();
    }

    public Query<Instant> asInstant() {
        return newQuery().asInstant();
    }

    public <T> Query<T> asClass(final Class<T> clazz) {
        return newQuery().asClass(clazz);
    }

    public <T> Query<T> asType(final TypeReference<T> type) {
        return newQuery().asType(type);
    }

    public Query<IonValue> asIon() {
        return newQuery().asIon();
    }

    private Query<?> newQuery() {
        return new Query<>(this);
    }

    /**
     * Finds all values from the config that match the given predicates combined with the default predicates.
     *
     * @param additionalPredicates Any additional properties to add to the default properties. Can be null.
     * @return A {@code Map<String, IonValue>} containing all the values matching the predicates.
     */
    LookupResult lookupValues(final Map<String, CriteriaPredicate> additionalPredicates) {
        // exit early if there is nothing new to lookup
        if (additionalPredicates == null || additionalPredicates.isEmpty() || defaultPredicates.equals(additionalPredicates)) {
            return new LookupResult(defaultPredicates, defaultValues);
        }

        // lookup new values by combining the new predicates with the default ones
        final Map<String, CriteriaPredicate> combinedPredicates = new HashMap<>(defaultPredicates);
        combinedPredicates.putAll(additionalPredicates);
        final Map<String, IonValue> values = globalConfigManager.getValuesForPredicates(namespace, combinedPredicates);
        return new LookupResult(Collections.unmodifiableMap(combinedPredicates), Collections.unmodifiableMap(values));
    }

    <T> Query.IonMapper<T> getClassConverter(final Class<T> clazz) {
        return ionValue -> ionMapper.readValue(ionValue, clazz);
    }

    <T> Query.IonMapper<T> getTypeConverter(final TypeReference<T> type) {
        return ionValue -> ionMapper.readValue(ionValue, type);
    }

}
