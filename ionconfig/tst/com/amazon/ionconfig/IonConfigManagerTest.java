// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonList;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IonConfigManagerTest {

    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();

    private static final IonString NOT_SKU = ION_SYSTEM.newString("not B0000SKU3");

    @ParameterizedTest(name = "When namespace={0}, input={1} then expected={2}")
    @MethodSource
    void skuConfigTest(final String namespace, final Map<String, String> input, final Map<String, IonValue> expected) {
        final IonConfigManager configManager = IonConfigManager.getInstance();
        assertEquals(expected, configManager.getValuesForProperties(namespace, input));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> skuConfigTest() {
        return Stream.of(
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newInt(123));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    return arguments("Skus", Collections.emptyMap(), expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newString("hello"));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    return arguments("Skus", Collections.singletonMap("seller", "1234"), expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newDecimal(35.6));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("seller", "1234");
                    input.put("category", "001234321");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newInt(12));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    return arguments("Skus", Collections.singletonMap("category", "001234321"), expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newInt(12));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    return arguments("Skus", Collections.singletonMap("category", "001237865"), expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234));
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(404939)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU1");
                    input.put("category", "001234321");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234));
                    final IonStruct subStruct = ION_SYSTEM.newEmptyStruct();
                    subStruct.add("subSubField", ION_SYSTEM.newInt(432432));
                    struct.add("subStruct", subStruct);
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(404939)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU1");
                    input.put("category", "001234321");
                    input.put("seller", "123231");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234));
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(404939)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU1");
                    input.put("category", "001237865");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234398));
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(4049394)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notConditionedfeatureFlagExample", ION_SYSTEM.newString("Hello!"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU2");
                    input.put("category", "001234321");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234398));
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(4049394), ION_SYSTEM.newInt(203897432)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notConditionedfeatureFlagExample", ION_SYSTEM.newString("Hello!"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU2");
                    input.put("category", "001234321");
                    input.put("seller", "123231");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    final IonStruct struct = ION_SYSTEM.newEmptyStruct();
                    struct.add("subField", ION_SYSTEM.newInt(1234398));
                    expected.put("field1", struct);
                    expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(4049394)));
                    expected.put("field3", ION_SYSTEM.newString("bar"));
                    expected.put("notConditionedfeatureFlagExample", ION_SYSTEM.newString("Hello!"));
                    expected.put("notExample", NOT_SKU);
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU2");
                    input.put("category", "001237865");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newInt(12));
                    expected.put("field3", ION_SYSTEM.newString("foo"));
                    final Map<String, String> input = new HashMap<>();
                    input.put("sku", "B0000SKU3");
                    input.put("category", "001237865");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, IonValue> expected = new HashMap<>();
                    expected.put("field1", ION_SYSTEM.newInt(123));
                    expected.put("field3", ION_SYSTEM.newString("fib"));
                    final Map<String, String> input = Collections.singletonMap("category", "value-has-multiple-hyphens");
                    return arguments("Skus", input, expected);
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "false");
                    input.put("field2", "false");
                    input.put("field3", "false");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(1)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "true");
                    input.put("field2", "false");
                    input.put("field3", "false");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(2)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "true");
                    input.put("field2", "true");
                    input.put("field3", "false");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(3)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "true");
                    input.put("field2", "true");
                    input.put("field3", "true");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(4)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "false");
                    input.put("field2", "true");
                    input.put("field3", "false");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(5)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "false");
                    input.put("field2", "true");
                    input.put("field3", "true");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(5)));
                }),
                makeArgs(() -> {
                    final Map<String, String> input = new HashMap<>();
                    input.put("field1", "false");
                    input.put("field2", "false");
                    input.put("field3", "true");
                    return arguments("Example", input, Collections.singletonMap("myField", ION_SYSTEM.newInt(1)));
                }));
    }

    @Test
    void example2Test_myField2() {
        final IonConfigManager configManager = IonConfigManager.getInstance();
        assertEquals(ION_SYSTEM.newInt(1), configManager.getValuesForProperties("Example2", Collections.emptyMap()).get("myField2"));

        final Map<String, String> onlyCriteria1 = new HashMap<>();
        onlyCriteria1.put("criteria1", "true");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", onlyCriteria1).get("myField2"));

        final Map<String, String> onlyCriteria2 = new HashMap<>();
        onlyCriteria2.put("criteria2", "true");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", onlyCriteria2).get("myField2"));

        final Map<String, String> onlyCriteria3 = new HashMap<>();
        onlyCriteria3.put("criteria3", "true");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", onlyCriteria3).get("myField2"));

        final Map<String, String> everythingFalse = new HashMap<>();
        everythingFalse.put("criteria1", "false");
        everythingFalse.put("criteria2", "false");
        everythingFalse.put("criteria3", "false");
        assertEquals(ION_SYSTEM.newInt(1), configManager.getValuesForProperties("Example2", everythingFalse).get("myField2"));

        final Map<String, String> everythingTrue = new HashMap<>();
        everythingTrue.put("criteria1", "true");
        everythingTrue.put("criteria2", "true");
        everythingTrue.put("criteria3", "true");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", everythingTrue).get("myField2"));

        final Map<String, String> criteria1True = new HashMap<>();
        criteria1True.put("criteria1", "true");
        criteria1True.put("criteria2", "false");
        criteria1True.put("criteria3", "false");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", criteria1True).get("myField2"));

        final Map<String, String> criteria2True = new HashMap<>();
        criteria2True.put("criteria1", "false");
        criteria2True.put("criteria2", "true");
        criteria2True.put("criteria3", "false");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", criteria2True).get("myField2"));

        final Map<String, String> criteria3True = new HashMap<>();
        criteria3True.put("criteria1", "false");
        criteria3True.put("criteria2", "false");
        criteria3True.put("criteria3", "true");
        assertEquals(ION_SYSTEM.newInt(2), configManager.getValuesForProperties("Example2", criteria3True).get("myField2"));
    }

    @Test
    void example2Test_listExample() {
        final IonList emptyList = ION_SYSTEM.newList();
        final IonList filledList = ION_SYSTEM.newList(ION_SYSTEM.newInt(3));

        final IonConfigManager configManager = IonConfigManager.getInstance();
        assertEquals(emptyList, configManager.getValuesForProperties("Example2", Collections.emptyMap()).get("listExample"));

        final Map<String, String> onlyCriteria1 = new HashMap<>();
        onlyCriteria1.put("criteria1", "true");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", onlyCriteria1).get("listExample"));

        final Map<String, String> onlyCriteria2 = new HashMap<>();
        onlyCriteria2.put("criteria2", "true");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", onlyCriteria2).get("listExample"));

        final Map<String, String> onlyCriteria3 = new HashMap<>();
        onlyCriteria3.put("criteria3", "true");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", onlyCriteria3).get("listExample"));

        final Map<String, String> everythingFalse = new HashMap<>();
        everythingFalse.put("criteria1", "false");
        everythingFalse.put("criteria2", "false");
        everythingFalse.put("criteria3", "false");
        assertEquals(emptyList, configManager.getValuesForProperties("Example2", everythingFalse).get("listExample"));

        final Map<String, String> everythingTrue = new HashMap<>();
        everythingTrue.put("criteria1", "true");
        everythingTrue.put("criteria2", "true");
        everythingTrue.put("criteria3", "true");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", everythingTrue).get("listExample"));

        final Map<String, String> criteria1True = new HashMap<>();
        criteria1True.put("criteria1", "true");
        criteria1True.put("criteria2", "false");
        criteria1True.put("criteria3", "false");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", criteria1True).get("listExample"));

        final Map<String, String> criteria2True = new HashMap<>();
        criteria2True.put("criteria1", "false");
        criteria2True.put("criteria2", "true");
        criteria2True.put("criteria3", "false");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", criteria2True).get("listExample"));

        final Map<String, String> criteria3True = new HashMap<>();
        criteria3True.put("criteria1", "false");
        criteria3True.put("criteria2", "false");
        criteria3True.put("criteria3", "true");
        assertEquals(filledList, configManager.getValuesForProperties("Example2", criteria3True).get("listExample"));
    }

    @Test
    void veryLargeNestedOr() {
        final IonConfigManager configManager = IonConfigManager.getInstance();
        final Map<String, String> inputs = new HashMap<>();
        inputs.put("level1", "00");
        inputs.put("level2", "00");
        inputs.put("level3", "00");
        inputs.put("level4", "00");
        inputs.put("level5", "00");
        inputs.put("level6", "00");
        inputs.put("level7", "00");
        inputs.put("level8", "00");
        inputs.put("level9", "00");
        inputs.put("level10", "00");
        assertEquals(Collections.singletonMap("myValue", ION_SYSTEM.newBool(true)), configManager.getValuesForProperties("VeryLargeNestedOrTest", inputs));
    }

    /**
     * Test the exact case specified in the Readme.
     */
    @Test
    void products() {
        final IonConfigManager configManager = IonConfigManager.getInstance();
        final Map<String, String> inputs = new HashMap<>();
        inputs.put("websiteFeatureGroup", "wireless");
        inputs.put("department", "111");
        inputs.put("category", "555");
        inputs.put("subcategory", "1234");

        final IonStruct expectedPromoMessaging = ION_SYSTEM.newEmptyStruct();
        expectedPromoMessaging.put("name", ION_SYSTEM.newString("promoMessaging"));
        expectedPromoMessaging.put("template", ION_SYSTEM.newString("customTemplate1"));

        final IonStruct expectedPrice = ION_SYSTEM.newEmptyStruct();
        expectedPrice.put("name", ION_SYSTEM.newString("price"));
        expectedPrice.put("template", ION_SYSTEM.newString("wireless"));
        expectedPrice.put("modules", ION_SYSTEM.newList(
                ION_SYSTEM.newString("businessPricing"),
                ION_SYSTEM.newString("rebates"),
                ION_SYSTEM.newString("quantityPrice"),
                ION_SYSTEM.newString("points"),
                ION_SYSTEM.newString("globalStoreIfd"),
                expectedPromoMessaging,
                ION_SYSTEM.newString("samplingBuyBox")));

        final IonValue expectedLayout = ION_SYSTEM.newList(
                ION_SYSTEM.newSymbol("brand"),
                ION_SYSTEM.newSymbol("title"),
                ION_SYSTEM.newSymbol("customerReviews"),
                expectedPrice);

        assertEquals(Collections.singletonMap("layout", expectedLayout), configManager.getValuesForProperties("Products", inputs));
    }

    @Test
    void noCriteriaTest() {
        final IonConfigManager configManager = IonConfigManager.getInstance();
        assertEquals(
                Collections.singletonMap("myField", ION_SYSTEM.newInt(123)),
                configManager.getValuesForProperties("NoCriteriaExample", Collections.emptyMap()));
    }

    @ParameterizedTest
    @MethodSource
    void criteriaWithCondition(final IonConfigManager configManager) {
        final Map<String, IonValue> expected = new HashMap<>();
        final IonStruct struct = ION_SYSTEM.newEmptyStruct();
        struct.add("subField", ION_SYSTEM.newInt(1234398));
        expected.put("field1", struct);
        expected.put("field2", ION_SYSTEM.newList(ION_SYSTEM.newInt(12345)));
        expected.put("field3", ION_SYSTEM.newString("bar"));
        expected.put("specialField1", ION_SYSTEM.newString("special1"));
        expected.put("specialField2", ION_SYSTEM.newString("special2"));
        expected.put("notExample", NOT_SKU);

        // create standard properties
        final Map<String, String> input = new HashMap<>();
        input.put("sku", "B0000SKU2");
        input.put("category", "001237865");

        // define custom checker, in this case for checking if a featureFlag is in treatment
        final CriteriaPredicate featureFlagChecker = CriteriaPredicate.fromCondition(featureFlag -> {
            final String[] rawfeatureFlag = featureFlag.split(":");
            return "EXAMPLE_12345".equals(rawfeatureFlag[0]) && "T1".equals(rawfeatureFlag[1]);
        });

        // define predicate with multiple values
        final CriteriaPredicate sellers = CriteriaPredicate.fromValues("2345", "3456");

        // convert the original property map to a critera map
        final Map<String, CriteriaPredicate> predicateMap = CriteriaPredicate.convertStringMap(input);
        predicateMap.put("featureFlag", featureFlagChecker);
        predicateMap.put("seller", sellers);

        assertEquals(expected, configManager.getValuesForPredicates("Skus", predicateMap));
    }

    /**
     * A Stream of IonConfigManagers that should all be configured identically.
     */
    @SuppressWarnings("unused")
    private static Stream<IonConfigManager> criteriaWithCondition() {
        final File file1 = new File("ion-cascading-config/FileToCombine.ion");
        final File file2 = new File("ion-cascading-config/PrioritizationDefinition.ion");
        final File file3 = new File("ion-cascading-config/NotAnIonFileAndShouldBeIgnored");
        return Stream.of(
                IonConfigManager.getInstance(),
                IonConfigManager.fromDirectory("ion-cascading-config"),
                IonConfigManager.fromDirectory(new File("ion-cascading-config")),
                IonConfigManager.fromFiles(file1, file2, file3),
                makeArgs(() -> {
                    try {
                        final IonDatagram datagram1 = ION_SYSTEM.getLoader().load(file1);
                        final IonDatagram datagram2 = ION_SYSTEM.getLoader().load(file2);
                        final IonDatagram datagram = mergeDatagrams(Arrays.asList(datagram1, datagram2));
                        return IonConfigManager.fromDatagram("datagram", datagram);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }),
                makeArgs(() -> {
                    try {
                        final IonDatagram datagram1 = ION_SYSTEM.getLoader().load(file1);
                        final IonDatagram datagram2 = ION_SYSTEM.getLoader().load(file2);
                        final Iterator<IonConfigRecord> iterator = Stream.concat(
                                datagram1.stream().map(it -> new IonConfigRecord("FileToCombine.ion", it)),
                                datagram2.stream().map(it -> new IonConfigRecord("PrioritizationDefinition.ion", it)))
                                .iterator();
                        return IonConfigManager.fromRecords(iterator);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    /**
     * Merges a collection of IonDatagrams into a single IonDatagram by cloning all the individual IonValues from them
     * and adding them to a single IonDatagram.
     */
    private static IonDatagram mergeDatagrams(final Collection<IonDatagram> datagrams) {
        return datagrams.stream()
                .flatMap(datagram -> datagram.stream().map(IonValue::clone))
                .collect(Collectors.toCollection(ION_SYSTEM::newDatagram));
    }

    /**
     * Helper method to wrap logic that produces a single output. Can be used as follows:
     * <pre>{@code
     * makeArgs(() -> {
     *     // do some stuff here and return a result. All local variables are scoped to just this block.
     * })
     * }</pre>
     */
    private static <T> T makeArgs(final Supplier<T> supplier) {
        return supplier.get();
    }
}
