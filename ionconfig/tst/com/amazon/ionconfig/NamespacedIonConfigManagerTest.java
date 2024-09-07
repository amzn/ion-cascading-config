// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NamespacedIonConfigManagerTest {

    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    @Test
    void verifyGlobalBehavior() {
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("NamespacedIonConfigManagerTest");
        assertAll(
                () -> assertEquals(Optional.of("Global Default String"), configManager.asString().find("stringToFind")),
                () -> assertEquals(Optional.of("Global Default Symbol"), configManager.asString().find("symbolToFindAsString")),
                () -> assertEquals(Optional.of(12345), configManager.asInteger().find("intToFind")),
                () -> assertEquals(Optional.of(45.67), configManager.asDouble().find("doubleToFind")),
                () -> assertEquals(Optional.of(new Date(Instant.parse("2018-01-02T01:23:45.678Z").toEpochMilli())), configManager.asDate().find("dateToFind")),
                () -> assertEquals(Optional.of(Instant.parse("2018-01-02T01:23:45.678Z")), configManager.asInstant().find("dateToFind")),
                () -> assertEquals(Optional.of(true), configManager.asBoolean().find("booleanToFind")),
                () -> assertEquals(Optional.of(new Person("Alice", 99)), configManager.asClass(Person.class).find("classToFind")),
                () -> assertEquals(
                        Optional.of(Arrays.asList("String 1", "String 2", "String 3", "String 4", "String 5")),
                        configManager.asType(new TypeReference<List<String>>() {
                        }).find("listToFind")),
                () -> assertEquals(
                        Optional.of(Stream.of(
                                new SimpleEntry<>("field1", 1),
                                new SimpleEntry<>("field2", 2),
                                new SimpleEntry<>("field3", 3),
                                new SimpleEntry<>("field4", 4),
                                new SimpleEntry<>("field5", 5))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))),
                        configManager.asType(new TypeReference<Map<String, Integer>>() {
                        }).find("mapToFind")),
                () -> {
                    final Map<String, Map<String, List<Integer>>> map = Collections.singletonMap("field1", Collections.singletonMap(
                            "subField",
                            Arrays.asList(1234, 5678)));
                    assertEquals(Optional.of(map), configManager.asType(new TypeReference<Map<String, Map<String, List<Integer>>>>() {
                    }).find("deepMapToFind"));
                });
    }

    @Test
    void verifyDevoBehavior() {
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager(
                "NamespacedIonConfigManagerTest",
                Collections.singletonMap("domain", "test"));
        assertAll(
                () -> assertEquals(Optional.of("Global Default String Test"), configManager.asString().find(("stringToFind"))),
                () -> assertEquals(Optional.of("Global Default Symbol Test"), configManager.asString().find("symbolToFindAsString")),
                () -> assertEquals(Optional.of(123456), configManager.asInteger().find("intToFind")),
                () -> assertEquals(Optional.of(45.678), configManager.asDouble().find("doubleToFind")),
                () -> assertEquals(Optional.of(new Date(Instant.parse("2018-01-02T01:23:45.679Z").toEpochMilli())), configManager.asDate().find("dateToFind")),
                () -> assertEquals(Optional.of(Instant.parse("2018-01-02T01:23:45.679Z")), configManager.asInstant().find("dateToFind")),
                () -> assertEquals(Optional.of(true), configManager.asBoolean().find("booleanToFind")),
                () -> assertEquals(Optional.of(false), configManager.asBoolean().withProperty("realm", "USAmazon").find("booleanToFind")),
                () -> assertEquals(Optional.of(new Person("Alice Test", 999)), configManager.asClass(Person.class).find("classToFind")),
                () -> assertEquals(
                        Optional.of(Arrays.asList("String 1", "String 2", "String 3", "String 4", "String 5", "String 6")),
                        configManager.asType(new TypeReference<List<String>>() {
                        }).find("listToFind")),
                () -> assertEquals(
                        Optional.of(Collections.emptyList()),
                        configManager.asType(new TypeReference<List<String>>() {
                        }).withProperty("realm", "USAmazon").find("listToFind")),
                () -> assertEquals(
                        Optional.of(Stream.of(
                                new SimpleEntry<>("field1", 1),
                                new SimpleEntry<>("field2", 2),
                                new SimpleEntry<>("field3", 3),
                                new SimpleEntry<>("field4", 4),
                                new SimpleEntry<>("field5", 5),
                                new SimpleEntry<>("field6", 6))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))),
                        configManager.asType(new TypeReference<Map<String, Integer>>() {
                        }).find("mapToFind")),
                () -> {
                    final Map<String, Map<String, List<Integer>>> map = Collections.singletonMap("field1", Collections.singletonMap(
                            "subField",
                            Arrays.asList(1234, 5679)));
                    assertEquals(Optional.of(map), configManager.asType(new TypeReference<Map<String, Map<String, List<Integer>>>>() {
                    }).find("deepMapToFind"));
                });
    }

    @Test
    void verifyManyCriteriaBehavior() {
        final Map<String, String> criteria = IntStream.range(0, 50)
                .mapToObj(i -> "criteria" + i)
                .collect(Collectors.toMap(Function.identity(), str -> "xxx"));
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("ManyCriteria", criteria);
        assertAll(
                () -> assertEquals(Integer.valueOf(123), configManager.asInteger().findOrThrow("myValue")),
                () -> assertEquals(Integer.valueOf(456), configManager.asInteger().withProperty("criteria50", "xxx").findOrThrow("myValue")));
    }

    @Test
    void verifyUSAmazonBehavior() {
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager(
                "NamespacedIonConfigManagerTest",
                Collections.singletonMap("realm", "USAmazon"));
        assertAll(
                () -> assertEquals(Optional.of("Global Default String"), configManager.asString().find("stringToFind")),
                () -> assertEquals(Optional.of("Global Default Symbol"), configManager.asString().find("symbolToFindAsString")),
                () -> assertEquals(Optional.of(12345), configManager.asInteger().find("intToFind")),
                () -> assertEquals(Optional.of(45.67), configManager.asDouble().find("doubleToFind")),
                () -> assertEquals(Optional.of(new Date(Instant.parse("2018-01-02T01:23:45.678Z").toEpochMilli())), configManager.asDate().find("dateToFind")),
                () -> assertEquals(Optional.of(Instant.parse("2018-01-02T01:23:45.678Z")), configManager.asInstant().find("dateToFind")),
                () -> assertEquals(Optional.empty(), configManager.asBoolean().find("booleanToFind")),
                () -> assertEquals(Optional.of(new Person("Alice", 99)), configManager.asClass(Person.class).find("classToFind")),
                () -> assertEquals(
                        Optional.of(Collections.singletonList("Contains 1 value")),
                        configManager.asType(new TypeReference<List<String>>() {
                        }).find("listToFind")),
                () -> assertEquals(
                        Optional.of(Stream.of(
                                new SimpleEntry<>("field1", 1),
                                new SimpleEntry<>("field2", 2),
                                new SimpleEntry<>("field3", 3),
                                new SimpleEntry<>("field4", 4),
                                new SimpleEntry<>("field5", 5))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))),
                        configManager.asType(new TypeReference<Map<String, Integer>>() {
                        }).find("mapToFind")),
                () -> {
                    final Map<String, Map<String, List<Integer>>> map = Collections.singletonMap("field1", Collections.singletonMap(
                            "subField",
                            Arrays.asList(1234, 5678)));
                    assertEquals(Optional.of(map), configManager.asType(new TypeReference<Map<String, Map<String, List<Integer>>>>() {
                    }).find("deepMapToFind"));
                });
    }

    @Test
    void verifyThrowsBehavior() {
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("NonExistentNamespace");
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> configManager.asString().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asInteger().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asLong().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asBigInteger().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asBigDecimal().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asDouble().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asBoolean().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asDate().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asInstant().findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asClass(String.class).findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asType(new TypeReference<List<String>>() {
                }).findOrThrow("myKey")),
                () -> assertThrows(IllegalStateException.class, () -> configManager.asIon().findOrThrow("myKey")));
    }

    @Test
    void cachedQueriesInvalidateWhenPropertiesChange() {
        final int[] evaluationCounter = new int[1];
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Skus");

        // cached query
        final Query<IonValue> query = setupQueryWithEvaluationCounter(configManager, evaluationCounter).cacheResults();

        // cache the value
        final IonValue value = query.findOrThrow("field2");
        assertEquals(ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)), value);
        assertEquals(2, evaluationCounter[0]);

        assertEquals(ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)), query.withProperty("testKey", "testValue").findOrThrow("field2"));
        assertEquals(4, evaluationCounter[0]);
    }

    @Test
    void changingTypesDoesNotInvalidateCachedValues() {
        final int[] evaluationCounter = new int[1];
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Skus");

        // make cached query
        final Query<IonValue> query = setupQueryWithEvaluationCounter(configManager, evaluationCounter).cacheResults();

        // cache the value
        final IonValue value = query.findOrThrow("field2");
        assertEquals(ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)), value);
        assertEquals(2, evaluationCounter[0]);

        // change the type and verify config was not re-evaluated
        assertEquals(Collections.singletonList(12345), query.asType(new TypeReference<List<Integer>>() {
        }).findOrThrow("field2"));
        assertEquals(2, evaluationCounter[0]);
    }

    @Test
    @DisplayName("Cached queries evaluate only once.")
    void cachedQueriesEvaluateOnce() {
        final int[] evaluationCounter = new int[1];
        final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Skus");

        // non-cached query
        final Query<IonValue> query = setupQueryWithEvaluationCounter(configManager, evaluationCounter);

        // fetch the value 10 times
        for (int i = 0; i < 10; i++) {
            final IonValue value = query.findOrThrow("field2");
            assertEquals(ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)), value);
        }

        // verify that the config was evaluated each time
        assertEquals(20, evaluationCounter[0], "Non-cached query should have evaluated the config multiple times.");

        // reset counter and set results to be cached
        evaluationCounter[0] = 0;
        query.cacheResults();

        // fetch the value 10 times again
        for (int i = 0; i < 10; i++) {
            final IonValue ionValue = query.findOrThrow("field2");
            assertEquals(ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)), ionValue);
        }

        // verify config is not evaluated more than once
        assertEquals(2, evaluationCounter[0], "Non-cached query should have evaluated the config multiple times.");
    }

    private Query<IonValue> setupQueryWithEvaluationCounter(final NamespacedIonConfigManager configManager, final int[] evaluationCounter) {
        return configManager.asIon()
                .withProperty("sku", "B0000SKU2")
                .withProperty("category", "001237865")
                .withPredicate("featureFlag", CriteriaPredicate.fromCondition(featureFlag -> {
                    final String[] rawFeatureFlag = featureFlag.split(":");
                    // adds 1 to the counter every time this featureFlag is specified in relevant config
                    evaluationCounter[0]++;
                    return "EXAMPLE_12345".equals(rawFeatureFlag[0]) && "T1".equals(rawFeatureFlag[1]);
                }))
                .withPredicate("seller", CriteriaPredicate.fromValues("2345", "3456"));
    }

    @Value
    @NoArgsConstructor(force = true)
    @AllArgsConstructor
    private static class Person {

        private final String name;
        private final int age;
    }
}
