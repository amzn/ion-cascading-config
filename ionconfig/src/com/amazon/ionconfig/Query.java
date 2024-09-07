// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonBool;
import com.amazon.ion.IonDecimal;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonText;
import com.amazon.ion.IonTimestamp;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * A context holding object that allows for a fluent API in combination with a NamespacedIonConfigManager. This class
 * can only be initially created from a factory method on a NamespacedIonConfigManager such as {@link
 * NamespacedIonConfigManager#asString()}. To copy this query and its current state to a new query of a different type
 * you can use any of the {@link #asString()} or {@link #asClass(Class)} related methods. All copied queries reuse the
 * same state internally, changing the properties of one will change the properties of all related copied queries. In
 * this way queries can be thought of as a typed view referencing the same internal state.
 * <p>
 * A Query object is mutable and is not thread-safe.
 *
 * @param <T> The type of data to find in config.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Query<T> {

    private final Function<IonValue, Optional<T>> converter;
    private final String type;
    private final NamespacedIonConfigManager configManager;
    private final QueryState state;

    private static class QueryState {

        private Map<String, CriteriaPredicate> additionalPredicates = createMap();
        private Map<String, Set<String>> additionalProperties = createMap();
        private boolean additionalPropertiesAdded;
        private boolean shouldCacheResults;
        private LookupResult cachedResults;
    }

    /**
     * Package-private constructor only taking the NamespacedIonConfigManager so it can be easily made from that object
     * then converted to the correct type.
     *
     * @param configManager
     */
    Query(final NamespacedIonConfigManager configManager) {
        this(null, null, configManager, new QueryState());
    }

    private Query(final Query<?> original, final Function<IonValue, Optional<T>> converter, final String type) {
        this(converter, type, original.configManager, original.state);
    }

    /**
     * Makes this query cache results or not based on the passed boolean. See {@link #cacheResults()} and {@link
     * #doNotCacheResults()} for behavior details.
     *
     * @param cacheResults A boolean deciding whether to cache results or not.
     * @return This query.
     */
    public Query<T> cacheResults(final boolean cacheResults) {
        this.state.shouldCacheResults = cacheResults;
        return this;
    }

    /**
     * Makes this query cache results when calling the {@code find} methods. This query will not re-evaluate the config
     * for subsequent {@code find} calls and instead reuse the previous results when finding values. The query will
     * re-evaluate the config if it's properties or predicates are changed or if {@link #clear()} is called.
     *
     * @return This query.
     */
    public Query<T> cacheResults() {
        return cacheResults(true);
    }

    /**
     * Makes this query not cache results when calling the {@code find} methods. This query will re-evaluate the config
     * for all subsequent {@code find} calls.
     *
     * @return This query.
     */
    public Query<T> doNotCacheResults() {
        return cacheResults(false);
    }

    /**
     * Adds a mapping of property keys to their allowed matching values. Does not override previously specified
     * properties for this Query.
     *
     * @return This Query.
     */
    public Query<T> withProperties(final Map<String, String> values) {
        values.forEach((key, value) -> this.state.additionalProperties.computeIfAbsent(key, k -> new HashSet<>()).add(value));
        this.state.additionalPropertiesAdded = true;
        return this;
    }

    /**
     * Adds a mapping of a property key to its allowed matching value. Does not override previously specified properties
     * for this Query.
     *
     * @return This Query.
     */
    public Query<T> withProperty(final String key, final String value) {
        this.state.additionalProperties.computeIfAbsent(key, k -> new HashSet<>()).add(value);
        this.state.additionalPropertiesAdded = true;
        return this;
    }

    /**
     * Adds a mapping of property keys to their criteria predicates.
     *
     * @return This Query.
     */
    public Query<T> withPredicates(final Map<String, CriteriaPredicate> predicates) {
        this.state.additionalPredicates.putAll(predicates);
        return this;
    }

    /**
     * Adds a mapping of a property key to its CriteriaPredicate.
     *
     * @return This Query.
     */
    public Query<T> withPredicate(final String key, final CriteriaPredicate predicate) {
        this.state.additionalPredicates.put(key, predicate);
        return this;
    }

    /**
     * Clears this finder of all specified criteria predicates and properties.
     *
     * @return This Query.
     */
    public Query<T> clear() {
        state.additionalPredicates = createMap();
        state.additionalProperties = createMap();
        state.additionalPropertiesAdded = false;
        return this;
    }

    /**
     * Finds a value with the name matching the key.
     *
     * @param key A String to find a value for.
     * @return An Optional containing the value or an empty Optional if none can be found.
     */
    public Optional<T> find(final String key) {
        return findKey(key, false);
    }

    /**
     * Finds a value with the name matching the key.
     *
     * @param key A String to find a value for.
     * @return The found value.
     * @throws IllegalStateException If no value can be found matching the key.
     */
    public T findOrThrow(final String key) {
        // .get() is safe here since throwIfEmpty=true, the NamespacedIonConfigManager will throw an IllegalStateException
        // if the Optional is empty. It throws the exception at the lower level because all the information is present
        // to make an effective error message. The Optional is passed up to allow more reuse of the code.
        return findKey(key, true).get();
    }

    private Optional<T> findKey(final String key, final boolean throwIfEmpty) {
        // convert properties to predicates and add to the predicates map, if anything has been added
        final LookupResult lookupResult = lookupAll();
        final IonValue value = lookupResult.getOutputValues().get(key);
        final Optional<T> converted = converter.apply(value);
        if (throwIfEmpty && !converted.isPresent()) {
            throw new IllegalStateException("Could not find " + type + " for key \"" + key + "\" with criteria " + lookupResult.getInputPredicates());
        }
        return converted;
    }

    /**
     * Finds all values that match this query.
     *
     * @return A {@code Map<String, IonValue>} containing all key-value pairs that match this query.
     */
    public Map<String, IonValue> findAll() {
        return lookupAll().getOutputValues();
    }

    private LookupResult lookupAll() {
        // convert properties to predicates and add to the predicates map, if anything has been added
        if (state.additionalPropertiesAdded) {
            state.additionalPredicates.putAll(CriteriaPredicate.convertStringSetMap(state.additionalProperties));

            // reset state so config is evaluated again
            state.additionalProperties = createMap();
            state.additionalPropertiesAdded = false;
            state.cachedResults = null;
        }

        // check if we should use the cached values
        final LookupResult resultToUse;
        if (state.shouldCacheResults) {

            // fetch the results if necessary, caching them to the state then return them.
            if (state.cachedResults == null) {
                state.cachedResults = configManager.lookupValues(state.additionalPredicates);
            }
            resultToUse = state.cachedResults;

        } else {

            // we don't want to cache the results so we should look them up and clear the cached value
            state.cachedResults = null;
            resultToUse = configManager.lookupValues(state.additionalPredicates);
        }

        return resultToUse;
    }

    /**
     * Initializes a mutable map with default settings.
     */
    private static <K, V> Map<K, V> createMap() {
        // set initial capacity to 0 since most of the time these maps will not have anything added
        return new HashMap<>(0);
    }

    public Query<String> asString() {
        return new Query<>(this, Query::convertToString, String.class.getCanonicalName());
    }

    public Query<Integer> asInteger() {
        return new Query<>(this, value -> Query.convertToBigInteger(value).map(BigInteger::intValueExact), Integer.class.getCanonicalName());
    }

    public Query<Long> asLong() {
        return new Query<>(this, value -> Query.convertToBigInteger(value).map(BigInteger::longValueExact), Long.class.getCanonicalName());
    }

    public Query<BigInteger> asBigInteger() {
        return new Query<>(this, Query::convertToBigInteger, BigInteger.class.getCanonicalName());
    }

    public Query<BigDecimal> asBigDecimal() {
        return new Query<>(this, Query::convertToBigDecimal, BigDecimal.class.getCanonicalName());
    }

    public Query<Double> asDouble() {
        return new Query<>(this, value -> Query.convertToBigDecimal(value).map(BigDecimal::doubleValue), Double.class.getCanonicalName());
    }

    public Query<Boolean> asBoolean() {
        return new Query<>(this, Query::convertToBoolean, Boolean.class.getCanonicalName());
    }

    public Query<Date> asDate() {
        return new Query<>(this, Query::convertToDate, Date.class.getCanonicalName());
    }

    public Query<Instant> asInstant() {
        return new Query<>(this, value -> Query.convertToDate(value).map(Date::toInstant), Instant.class.getCanonicalName());
    }

    public <K> Query<K> asClass(final Class<K> clazz) {
        return new Query<>(this, value -> convertToType(value, configManager.getClassConverter(clazz)), clazz.getCanonicalName());
    }

    public <K> Query<K> asType(final TypeReference<K> typeRef) {
        return new Query<>(this, value -> convertToType(value, configManager.getTypeConverter(typeRef)), typeRef.getType().getTypeName());
    }

    public Query<IonValue> asIon() {
        return new Query<>(this, Optional::ofNullable, IonValue.class.getCanonicalName());
    }

    @FunctionalInterface
    interface IonMapper<T> {

        T map(IonValue value) throws IOException;
    }

    private static <T> Optional<T> convertToType(final IonValue value, final IonMapper<T> mapper) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.map(value));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<String> convertToString(final IonValue value) {
        if (value == null || value.isNullValue() || !IonType.isText(value.getType())) {
            return Optional.empty();
        }
        return Optional.ofNullable(((IonText) value).stringValue());
    }

    private static Optional<BigInteger> convertToBigInteger(final IonValue value) {
        if (value == null || value.isNullValue() || value.getType() != IonType.INT) {
            return Optional.empty();
        }
        return Optional.ofNullable(((IonInt) value).bigIntegerValue());
    }

    private static Optional<BigDecimal> convertToBigDecimal(final IonValue value) {
        if (value == null || value.isNullValue() || value.getType() != IonType.DECIMAL) {
            return Optional.empty();
        }
        return Optional.ofNullable(((IonDecimal) value).bigDecimalValue());
    }

    private static Optional<Boolean> convertToBoolean(final IonValue value) {
        if (value == null || value.isNullValue() || value.getType() != IonType.BOOL) {
            return Optional.empty();
        }
        return Optional.of(((IonBool) value).booleanValue());
    }

    private static Optional<Date> convertToDate(final IonValue value) {
        if (value == null || value.isNullValue() || value.getType() != IonType.TIMESTAMP) {
            return Optional.empty();
        }
        return Optional.ofNullable(((IonTimestamp) value).dateValue());
    }

}
