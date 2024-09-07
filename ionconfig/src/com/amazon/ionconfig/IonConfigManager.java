// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ionconfig;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonList;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonText;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This project aims to provide a flexible configuration system that allows users to define arbitrary hierarchies of
 * properties and combine them in various ways. See the tests for example usage. Currently only "and" and "or"
 * combinations are implemented, "not" should be added in a future update. Only complete overwriting of values are
 * implemented for cascading configuration, in the future "add" and "remove" functionality can be added to give more
 * control for IonContainers such as IonStructs or IonLists.
 * <p>
 * To use this system, depend on this package then add configuration files to the "ion-cascading-config" directory at
 * the root of an Apollo environment. The configuration files must end with ".ion" or they will be ignored. All
 * configuration files found in the directory will be loaded and separated by their namespaces. Define a namespace as an
 * IonStruct annotated with "namespace" (case-insensitive), followed by the namespace name (case-sensitive). The Struct
 * must contain a field named "prioritizedCriteria" which must be a list of IonText (symbols or strings) which is the
 * list of possible properties that can be used to configure within that namespace. The list will be prioritized in
 * order of least to greatest priority. For example:
 * <pre>{@code
 * Namespace::Example::{
 *     prioritizedCriteria:[
 *         field1,
 *         field2,
 *         field3
 *     ]
 * }
 * }</pre>
 * <p>
 * In this example, the namespace is named "Example" and the possible usable fields are "field1", "field2", and
 * "field3". "field3" has the greatest priority and "field1" has the least priority, so if there are configured values
 * all three fields, field1 will be used unless field2 is set in which field2 will be used, overriding the values set by
 * "field1". If field1 defines values that are not defined by field2, they will not be overridden and remain in the
 * result. Continuing the example:
 * <pre>{@code
 * Example::{
 *     myField:1,
 *     'field1-true':{
 *         myField:2,
 *         'field2-true':{
 *             myField:3,
 *             'field3-true':{
 *                 myField:4
 *             }
 *         }
 *     },
 *     'field2-true':{
 *         myField:5
 *     }
 * }
 * }</pre>
 * <p>
 * In this example, the expected results would follow given the different combinations of criteria.
 * <pre>{@code
 *     field1:false, field2:false, field3:false => myField:1 // uses the global values
 *     field1:true,  field2:false, field3:false => myField:2
 *     field1:true,  field2:true,  field3:false => myField:3
 *     field1:true,  field2:true,  field3:true  => myField:4
 *     field1:false, field2;true,  field3:false => myField:5
 *     field1:false, field2;true,  field3:true  => myField:5
 *     field1:false, field2;false, field3:true  => myField:1
 * }</pre>
 * <p>
 * Once the configurations are defined, the following code can be used to get an instance of the IonConfigManager and
 * use it.
 *
 * <pre>{@code
 *
 * // Get the IonConfigManager instance, the instance is only loaded once per JVM instance and loaded values are kept
 * // in memory so there are no concerns about getting the instance multiple times, even across multiple threads since
 * // it uses the Lazy Holder pattern which guarantees it is only loaded once.
 * final IonConfigManager configManager = IonConfigManager.getInstance();
 *
 * // specify namespace, you can use a NamespacedIonConfigManager that always uses the same namespace so it
 * // doesn't need to be specified every time.
 * final String namespace = "Example";
 *
 * // You can use a NamespacedIonConfigManager that includes default settings like domain and realm so they
 * // don't need to be specified every time.
 * final Map<String, String> properties = new HashMap<>();
 * properties.put("field1", "true");
 * properties.put("field2", "true");
 * properties.put("field3", "true");
 *
 * // values will contain "myField" => 4
 * final Map<String, IonValue> values = configManager.getValuesForProperties(namespace, properties);
 * }</pre>
 * <p>
 * IonConfigManagers are thread-safe and immutable once constructed, assuming the underlying IonValues are not modified
 * after it is constructed.
 */
public class IonConfigManager {

    static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();
    static final String SUB_FIELD_VALUE_KEYWORD = "value";

    private static final String DEFAULT_DIRECTORY = "ion-cascading-config";
    private static final String ALLOWED_EXTENSION = ".ion";
    private static final String NAMESPACE_ANNOTATION = "namespace";
    private static final String NAMESPACE_PRIORITIES_FIELD = "prioritizedCriteria";
    private static final String SUB_LIST_VALUE_KEYWORD = "values";
    private static final Set<String> SUB_FIELD_VALUE_KEYWORD_SET = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(SUB_LIST_VALUE_KEYWORD, SUB_FIELD_VALUE_KEYWORD)));

    private static final String RECORD_ERROR_PREFIX = "Record: %s, Error: ";
    private static final String NAMESPACE_DECLARATION_ERROR_MESSAGE = RECORD_ERROR_PREFIX + "A namespace declaration is incorrect. Syntax should be "
            + "'namespace'::'YourNamespace'::{prioritizedCriteria:[/*Define your priorities as a list of symbols or strings.*/]} but was %s.";

    private final Map<String, List<MatchableProperty>> namespacedProperties;

    private IonConfigManager(final Stream<IonConfigRecord> records) {
        final Map<String, List<String>> namespacedPriorities = new HashMap<>();

        // a map to keep track of all property lists that we must sort after the config is parsed
        final Map<String, List<List<MatchableProperty>>> namespacedPropertyListsToSort = new HashMap<>();
        this.namespacedProperties = new HashMap<>();

        records.forEach(record -> {
            final Object name = record.getName();
            final IonValue ionValue = record.getValue();
            verify(ionValue != null, RECORD_ERROR_PREFIX + "Found null value with no namespace.", name);

            verify(isStruct(ionValue), NAMESPACE_DECLARATION_ERROR_MESSAGE, name, ionValue);
            final IonStruct namespacedConfig = (IonStruct) ionValue;

            final String[] annotations = ionValue.getTypeAnnotations();
            verify(annotations != null && annotations.length > 0, RECORD_ERROR_PREFIX + "Found unnamespaced config.", name);

            if (NAMESPACE_ANNOTATION.equals(annotations[0].toLowerCase())) {
                verify(annotations.length == 2, NAMESPACE_DECLARATION_ERROR_MESSAGE, name, ionValue);

                final String namespace = annotations[1];
                verify(!namespacedPriorities.containsKey(namespace), RECORD_ERROR_PREFIX + "Namespace %s is declared more than once.", name, namespace);

                final IonValue rawPriorities = namespacedConfig.get(NAMESPACE_PRIORITIES_FIELD);
                verify(
                        rawPriorities != null && !rawPriorities.isNullValue() && rawPriorities.getType() == IonType.LIST,
                        NAMESPACE_DECLARATION_ERROR_MESSAGE,
                        name,
                        ionValue);

                final List<String> priorities = ((IonList) rawPriorities).stream()
                        .map(priority -> {
                            verify(
                                    priority != null && !priority.isNullValue() && IonType.isText(priority.getType()),
                                    NAMESPACE_DECLARATION_ERROR_MESSAGE,
                                    name,
                                    ionValue);
                            return ((IonText) priority).stringValue();
                        })
                        .collect(Collectors.toList());
                namespacedPriorities.put(namespace, priorities);
                return;
            }

            // created a list of properties for each namespace, this will be sorted and filtered according to the
            // priorities after everything has been read. The priorities won't all be guaranteed to be read until this
            // stream is completed.
            final String namespace = annotations[0];
            final List<List<MatchableProperty>> propertyListsToSort = namespacedPropertyListsToSort.computeIfAbsent(namespace, key -> new ArrayList<>());
            final List<MatchableProperty> properties = parseMatchablePropertiesRecursive(
                    name,
                    namespacedConfig,
                    Collections.emptyList(),
                    propertyListsToSort)
                            .collect(Collectors.toList());
            // combine all configurations for the same namespace together, the first annotation is the namespace
            this.namespacedProperties.computeIfAbsent(namespace, key -> new ArrayList<>()).addAll(properties);
        });

        // add the top-level property list to the sort map so it is also sorted
        this.namespacedProperties.forEach((namespace, properties) -> namespacedPropertyListsToSort.get(namespace).add(properties));

        // begin filtering and sorting configurations now that all configurations have been loaded
        final Set<String> unfilteredNamespaces = new HashSet<>(namespacedPropertyListsToSort.keySet());
        if (namespacedPropertyListsToSort.keySet().retainAll(namespacedPriorities.keySet())) {
            unfilteredNamespaces.removeAll(namespacedPropertyListsToSort.keySet());
            throw new IonConfigException("Found %s undeclared namespaces. %s", unfilteredNamespaces.size(), unfilteredNamespaces);
        }

        namespacedPropertyListsToSort.forEach((namespace, propertyLists) -> {
            final List<String> priorities = namespacedPriorities.get(namespace);

            final Map<String, Integer> indexedPriorities = IntStream.range(0, priorities.size())
                    .boxed()
                    .collect(Collectors.toMap(priorities::get, Function.identity()));

            propertyLists.forEach(properties -> {
                // remove any MatchableProperties that contain an invalid criteria (if the criteria was not specified in the priorities list for this namespace)
                final List<MatchableProperty> invalidCriteria = properties.stream()
                        .filter(
                                matchableProperty -> matchableProperty.getCriteria()
                                        .stream()
                                        .anyMatch(criterion -> !indexedPriorities.containsKey(criterion.getName())))
                        .collect(Collectors.toList());
                verify(
                        invalidCriteria.isEmpty(),
                        "Namespace %s contains criteria which are not defined in its priorities. Invalid criteria:%s",
                        namespace,
                        invalidCriteria);

                // remove unnecessary matchable properties which contain no values
                properties.removeIf(matchableProperty -> matchableProperty.getValues().isEmpty());

                // sort each MatchableProperty's criteria list according to the priority of the individual fields
                // see README.md for more details on how the algorithm needs to perform

                final Comparator<GroupedCriteriaDefinition> criteriaComparator = Comparator.comparing(
                        GroupedCriteriaDefinition::getName,
                        Comparator.comparingInt(indexedPriorities::get).reversed());
                properties.forEach(matchableProperty -> matchableProperty.getCriteria().sort(criteriaComparator));

                // sort the entire list of MatchableProperties by their total priority

                // give each element of the list an order of magnitude more importance than all following elements so
                // a criteria is more important than all less criteria combined but that criteria combined with another
                // less criteria is more important than by itself.
                // For example if our prioritizedCriteria are [a, b, c, d, e, f, g, ... z]
                // then [a] < [b] < [y ... a] < [z] < [z, a] < [z, b, a] < [z, c] ...
                properties.sort(Comparator.comparing(MatchableProperty::getCriteria, Comparator.comparing(criteriaList -> {
                    final int prioritiesSize = indexedPriorities.size();

                    // use BigIntegers to prevent overflows when dealing with powers
                    final BigInteger prioritiesSizeBigInt = BigInteger.valueOf(prioritiesSize);
                    BigInteger matchablePropertyPriority = BigInteger.ZERO;
                    for (int i = 0; i < criteriaList.size(); i++) {
                        final String criteriaFieldName = criteriaList.get(i).getName();
                        // raise elements to magnitude size = priority size to ensure it is more important than all following elements
                        final BigInteger criteriaPriorityValue = prioritiesSizeBigInt.pow(prioritiesSize - i)
                                .multiply(BigInteger.valueOf(indexedPriorities.get(criteriaFieldName) + 1));
                        matchablePropertyPriority = matchablePropertyPriority.add(criteriaPriorityValue);
                    }
                    return matchablePropertyPriority;
                })));
            });
        });
    }

    public static IonConfigManager getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Constructs an IonConfigManager from the path of a directory by loading all ion files in the directory.
     *
     * Ignores all files without a "{@code .ion}" extension.
     */
    public static IonConfigManager fromDirectory(final String path) {
        return fromDirectory(new File(path));
    }

    /**
     * Constructs an IonConfigManager from a directory by loading all ion files in the directory.
     *
     * Ignores all files without a "{@code .ion}" extension.
     */
    public static IonConfigManager fromDirectory(final File directory) {
        return fromFiles(directory.listFiles());
    }

    /**
     * Constructs an IonConfigManager from one or more ion files.
     *
     * Ignores all files without a "{@code .ion}" extension.
     */
    public static IonConfigManager fromFiles(final File... files) {
        final Stream<IonConfigRecord> records = Arrays.stream(files)
                .filter(File::isFile)
                .filter(file -> ALLOWED_EXTENSION.equals(getFileExtension(file.getName())))
                .sorted(Comparator.comparing(File::getName)) // sort files by name to provide deterministic loading order
                .map(file -> {
                    final String filename = file.getName();
                    try {
                        return new SimpleImmutableEntry<>(filename, ION_SYSTEM.getLoader().load(file));
                    } catch (final IOException e) {
                        throw new IonConfigException(e, RECORD_ERROR_PREFIX + "Could not parse IonCascadingConfig file.", filename);
                    }
                })
                .flatMap(filenameToIonStream -> filenameToIonStream.getValue()
                        .stream()
                        .peek(IonValue::makeReadOnly) // ensure configuration is not modified after being loaded
                        .map(value -> new IonConfigRecord(filenameToIonStream.getKey(), value)));

        return fromRecords(records);
    }

    /**
     * Constructs an IonConfigManager from one or more IonConfigRecords.
     */
    public static IonConfigManager fromRecords(final IonConfigRecord... records) {
        return fromRecords(Arrays.stream(records));
    }

    /**
     * @see #fromRecords(IonConfigRecord...)
     */
    public static IonConfigManager fromRecords(final Iterator<IonConfigRecord> records) {
        return fromRecords(() -> records);
    }

    /**
     * @see #fromRecords(IonConfigRecord...)
     */
    public static IonConfigManager fromRecords(final Iterable<IonConfigRecord> records) {
        return fromRecords(StreamSupport.stream(records.spliterator(), false));
    }

    /**
     * @see #fromRecords(IonConfigRecord...)
     */
    public static IonConfigManager fromRecords(final Stream<IonConfigRecord> records) {
        return new IonConfigManager(records);
    }

    /**
     * Constructs an IonConfigManager from a single IonValue.
     */
    public static IonConfigManager fromValue(final Object name, final IonValue value) {
        return fromRecords(new IonConfigRecord(name, value));
    }

    /**
     * IonDatagrams can contain multiple IonValues at once, such as a file containing many values, so prefer this method
     * over {@link #fromValue(Object, IonValue)} when using IonDatagrams.
     */
    public static IonConfigManager fromDatagram(final Object name, final IonDatagram datagram) {
        return fromRecords(datagram.stream().map(value -> new IonConfigRecord(name, value)));
    }

    /**
     * Follow the Lazy Holder pattern to ensure that this class is never initialized twice and only ever initialized if
     * necessary.
     */
    private static class LazyHolder {

        private static final IonConfigManager INSTANCE = IonConfigManager.fromDirectory(DEFAULT_DIRECTORY);
    }

    /**
     * Processes the IonCascadingConfig to produce a resulting map of property keys to their Ion values. A criteria will
     * only be considered to pass if it is the same as the property value with the same key.
     *
     * @param namespace The namespace to check within.
     * @param properties A Map of property names to allowed values.
     * @return A Map of String to IonValues as a result of processing the config.
     */
    public Map<String, IonValue> getValuesForProperties(final String namespace, final Map<String, String> properties) {
        // check if the input property is contained in the configured property set
        return getValuesByCondition(namespace, entry -> entry.getValue().contains(properties.get(entry.getKey())));
    }

    /**
     * Allows callers to pass in custom methods to check the values of properties. The bulk condition will be passed a
     * Set of the criteria values from the config and if it returns true, the data values associated with that criteria
     * will be factored into the result, following normal cascading processing.
     * <p>
     * If the same config key is specified in config multiple times at the same prioritization, the particular value
     * that is chosen is non-deterministic and may vary between instances.
     *
     * @param namespace The namespace to check within.
     * @param predicates A Map of names to CriteriaPredicates.
     * @return A Map of String to IonValues as a result of processing the config.
     */
    public Map<String, IonValue> getValuesForPredicates(final String namespace, final Map<String, CriteriaPredicate> predicates) {
        return getValuesByCondition(
                namespace,
                criteria -> predicates.getOrDefault(criteria.getKey(), CriteriaPredicate.ALWAYS_FALSE).test(criteria.getValue()));
    }

    private Map<String, IonValue> getValuesByCondition(final String namespace, final Predicate<Entry<String, Set<String>>> condition) {
        final List<MatchableProperty> properties = namespacedProperties.getOrDefault(namespace, Collections.emptyList());
        final Map<String, IonProperty> aggregatedValues = cascadeMatchableProperties(properties, condition);
        return aggregatedValues.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().getIonValue(condition)));
    }

    /**
     * Parses an IonStruct to create a Stream of MatchableProperties that can be used later for cascading together to
     * retrieve a value.
     *
     * @param recordName The name of the file that this IonStruct is contained within.
     * @param config The IonStruct to parse.
     * @param currentCriteria The current list of criteria that scope this struct.
     * @param matchablePropertiesToSort A list of lists of MatchableProperties that must be sorted after all parsing is
     * completed. New lists of Matchable Properties may be added to it.
     * @return A Stream of MatchableProperties
     */
    private static Stream<MatchableProperty> parseMatchablePropertiesRecursive(
            final Object recordName,
            final IonStruct config,
            final List<GroupedCriteriaDefinition> currentCriteria,
            final List<List<MatchableProperty>> matchablePropertiesToSort) {
        if (config.isEmpty()) {
            return Stream.empty();
        }

        final Map<String, IonProperty> values = new HashMap<>();
        final MatchableProperty currentProperty = new MatchableProperty(currentCriteria, values);

        Stream<MatchableProperty> results = Stream.of(currentProperty);
        for (final IonValue ionValue : config) {
            final String fieldName = ionValue.getFieldName();

            // the ionValue can either be a criteria definition or a value, check for it being a value first to short-circuit
            final CriterionDefinition criterionDefinition = CriterionDefinition.parse(fieldName);
            if (criterionDefinition == null) {
                values.put(fieldName, parseIonPropertyRecursive(recordName, ionValue, matchablePropertiesToSort));
                continue;
            }

            // the ionValue is a valid criteria definition, continue parsing
            results = Stream.concat(
                    results,
                    parseCriteriaDefinitionsRecursive(recordName, ionValue, currentCriteria, matchablePropertiesToSort, criterionDefinition));
        }

        return results;
    }

    /**
     * Creates an IonProperty by parsing the given IonValue.
     *
     * @param recordName The name of the file that this IonValue is contained within.
     * @param ionValue The IonValue to parse.
     * @param matchablePropertiesToSort A list of lists of MatchableProperties that must be sorted after all parsing is
     * completed. New lists of Matchable Properties may be added to it.
     * @return An IonProperty
     */
    private static IonProperty parseIonPropertyRecursive(
            final Object recordName,
            final IonValue ionValue,
            final List<List<MatchableProperty>> matchablePropertiesToSort) {

        if (isStruct(ionValue) && StreamSupport.stream(((IonStruct) ionValue).spliterator(), false).anyMatch(IonConfigManager::couldBeDynamic)) {
            final List<MatchableProperty> subProperties = parseMatchablePropertiesRecursive(
                    recordName,
                    (IonStruct) ionValue,
                    Collections.emptyList(),
                    matchablePropertiesToSort)
                            .collect(Collectors.toList());
            matchablePropertiesToSort.add(subProperties);
            return new DynamicIonStruct(subProperties);
        }

        if (!ionValue.isNullValue() && ionValue.getType() == IonType.LIST && ((IonList) ionValue).stream().anyMatch(IonConfigManager::couldBeDynamic)) {
            final IonList list = (IonList) ionValue;
            final List<IonProperty> listProperties = list.stream()
                    .map(rawListValue -> {

                        // check if this a sub field
                        final String[] annotations = rawListValue.getTypeAnnotations();
                        if (annotations.length > 0 && CriterionDefinition.parse(annotations[0]) != null) {

                            verify(
                                    isStruct(rawListValue),
                                    RECORD_ERROR_PREFIX + "Criterion definition field must be a non-null struct but was a %s.",
                                    recordName,
                                    createTypeString(rawListValue));
                            final IonStruct structValue = (IonStruct) rawListValue;
                            verify(structValue.size() == 1, RECORD_ERROR_PREFIX + "a list sub-field criteria must contain exactly 1 value.", recordName);

                            // parse the sub field, add it to the sort list then verify that it has the necessary structure
                            final List<MatchableProperty> subField = parseCriteriaDefinitionsRecursive(
                                    recordName,
                                    rawListValue,
                                    Collections.emptyList(),
                                    matchablePropertiesToSort)
                                            .collect(Collectors.toList());

                            // remove unnecessary matchable properties which contain no values
                            subField.removeIf(matchableProperty -> matchableProperty.getValues().isEmpty());
                            matchablePropertiesToSort.add(subField);

                            subField.forEach(property -> {
                                verify(
                                        property.getValues().size() == 1,
                                        RECORD_ERROR_PREFIX + "a list sub-field criteria must contain exactly 1 value.",
                                        recordName);

                                final Entry<String, IonProperty> subFieldEntry = property.getValues().entrySet().iterator().next();
                                final String subFieldName = subFieldEntry.getKey();

                                // verify the field is one of the allowed names
                                if (!SUB_FIELD_VALUE_KEYWORD_SET.contains(subFieldName)) {
                                    throw new IonConfigException(
                                            RECORD_ERROR_PREFIX + "a sub-list criteria must contain exactly 1 field named one of %s but actually was %s",
                                            recordName,
                                            SUB_FIELD_VALUE_KEYWORD_SET,
                                            subFieldName);
                                }

                                // if it is "values" verify it is a list
                                if (SUB_LIST_VALUE_KEYWORD.equals(subFieldName)) {
                                    verify(
                                            property.getValues().get(SUB_LIST_VALUE_KEYWORD).isListBased(),
                                            RECORD_ERROR_PREFIX + "a sub-list criteria with name \"%s\" must be a list.",
                                            recordName,
                                            SUB_LIST_VALUE_KEYWORD);
                                }
                            });

                            return new DynamicIonSubField(subField);
                        }
                        return parseIonPropertyRecursive(recordName, rawListValue, matchablePropertiesToSort);
                    })
                    .collect(Collectors.toList());
            return new DynamicIonList(listProperties);
        }

        return new BasicIonProperty(ionValue);
    }

    /**
     * Verifies the given ionValue is a struct, treats all annotations as CriterionDefinitions, and then recursively
     * parses it for MatchableProperties.
     */
    private static Stream<MatchableProperty> parseCriteriaDefinitionsRecursive(
            final Object recordName,
            final IonValue ionValue,
            final List<GroupedCriteriaDefinition> currentCriteria,
            final List<List<MatchableProperty>> matchablePropertiesToSort,
            final CriterionDefinition... additionalCriterion) {

        verify(
                isStruct(ionValue),
                RECORD_ERROR_PREFIX + "Criterion definition field must be a non-null struct but was a %s.",
                recordName,
                createTypeString(ionValue));
        final IonStruct structValue = (IonStruct) ionValue;

        // group all "or" conditions together by criteria names and putting all the criteria values into a set for O(1) lookup
        final Map<CriterionIdentifier, Set<String>> combinedOrPropertiesMap = Stream.concat(
                Stream.of(additionalCriterion),
                Optional.ofNullable(structValue.getTypeAnnotations())
                        .map(Arrays::stream)
                        .orElse(Stream.empty())
                        .map(potentialCriteria -> {
                            final CriterionDefinition orCriterion = CriterionDefinition.parse(potentialCriteria);
                            verify(
                                    orCriterion != null,
                                    RECORD_ERROR_PREFIX + "Could not parse 'OR' criterion from string. It must be in the format 'key-value'. Input: %s",
                                    recordName,
                                    potentialCriteria);
                            return orCriterion;
                        }))
                .collect(Collectors.groupingBy(CriterionDefinition::getIdentifier, Collectors.mapping(CriterionDefinition::getValue, Collectors.toSet())));
        return combinedOrPropertiesMap.entrySet()
                .stream()
                .map(newCriteria -> {
                    final List<GroupedCriteriaDefinition> combinedOrProperties = new ArrayList<>(currentCriteria);
                    combinedOrProperties.add(new GroupedCriteriaDefinition(newCriteria.getKey(), newCriteria.getValue()));
                    return combinedOrProperties;
                })
                .flatMap(criteria -> parseMatchablePropertiesRecursive(recordName, structValue, criteria, matchablePropertiesToSort));
    }

    /**
     * Iterates over the sorted properties, checking if each one matches and if so, adding it to an aggregate map of
     * values.
     *
     * @param sortedProperties A sorted list of MatchableProperty.
     * @param condition A predicate to check for each matchable property to see if it should be added to the aggregate
     * map.
     * @return The aggregate map.
     */
    static Map<String, IonProperty> cascadeMatchableProperties(
            final List<MatchableProperty> sortedProperties,
            final Predicate<Entry<String, Set<String>>> condition) {
        final Map<String, IonProperty> aggregatedValues = new HashMap<>();
        sortedProperties.forEach(matchableProperty -> {
            // loop over matchable properties and aggregate their values into a single map
            // properties matched later might overwrite properties that were matched earlier, thus the cascading effect
            if (matchableProperty.getCriteria().stream().allMatch(criteriaDefinition -> criteriaDefinition.testCondition(condition))) {
                aggregatedValues.putAll(matchableProperty.getValues());
            }
        });
        return aggregatedValues;
    }

    private static String getFileExtension(final String filename) {
        final int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return filename.substring(lastIndexOf);
    }

    /**
     * Returns true if the IonValue is an IonStruct or IonList.
     */
    private static boolean couldBeDynamic(final IonValue ionValue) {
        if (ionValue.isNullValue()) {
            return false;
        }
        final IonType type = ionValue.getType();
        return type == IonType.LIST || type == IonType.STRUCT;
    }

    private static boolean isStruct(final IonValue ionValue) {
        return !ionValue.isNullValue() && ionValue.getType() == IonType.STRUCT;
    }

    private static String createTypeString(final IonValue ionValue) {
        return ionValue.isNullValue() ? "null" : ionValue.getType().toString();
    }

    private static void verify(final boolean condition, final String format, final Object... formatArgs) {
        if (!condition) {
            throw new IonConfigException(format, formatArgs);
        }
    }
}
