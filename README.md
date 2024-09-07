# Overview

Ion Cascading Config is a flexible and extensible configuration system using the [Ion data format](https://amzn.github.io/ion-docs/docs/spec.html) that allows you to configure almost any type of data and specify which criteria must be true to retrieve the data. This system was designed to maximize readability, extensibility, flexibility, as well as minimize configuration files and code. Since it uses the Ion format, users can use any type of data that Ion supports. This system also allows any arbitrary class or generic type that can be deserialized with [Jackson-dataformat-ion](https://github.com/FasterXML/jackson-dataformats-binary/tree/2.12/ion).

# Features

* **Ion format** - Uses the [Ion data format](https://amzn.github.io/ion-docs/docs/spec.html) which has integers and decimals of unlimited precision, string, timestamps, annotations, lists, and structs.
* **Deterministic loading** - When stored in files, the files are loaded in alphabetical order, ensuring the config is deterministic and reproducible.
* **Namespaced** - Configuration is namespaced, ensuring different config within the same environment do not interfere with each other.
* **Custom criteria**
  * Each namespace defines its own *criteria* that can be used to write its configuration with, allowing for complete customization and flexibility for any application.
  * The criteria are simply defined in a list, named as strings, in order of importance. Adding new criteria is a 1-line change!
  * Criteria may be combined in **AND**, **OR**, and **NOT** relationships.
  * By default, criteria are evaluated with a direct string equality check but arbitrary evaluation is supported by allowing users to pass in custom `CriteriaPredicates`. This could be used to call an API as part of a condition.
  * Lists and Structs may optionally include elements based on criteria as well with arbitrary depth and complexity.
* **SCSS-like cascading rules** - Configuration values are scoped using combination of criteria. All criteria that scope a value must be satisfied in order for it to be used. Values can be overriden if a more specific/important combination of criteria define it. Because criteria can nest other criteria, it makes the config very minimal.
* **Null-safe** - Configuration values can be fetched using the `NamespacedIonConfigManager` as an `Optional` value using `Query.find(key)`. If a value should always be present, it can be fetched directly using `Query.findOrThrow(key)`.
* **Built-in deserialization to classes** - [Jackson-dataformat-ion](https://github.com/FasterXML/jackson-dataformats-binary/tree/2.12/ion) support is built into the `NamespacedIonConfigManager` so custom classes or generic types can be deserialized from the configuration out-of-the-box.
* **Flexible config storage** - Config can be loaded from files or can be passed in as a function parameter. This allows you to store your configuration and load it from anywhere, including an external service or database.

# Getting Started

To use IonCascadingConfig you must depend on this package, define your config, then access it from your code using the IonConfigManager or the NamespacedIonConfigManager.

### Ion data format
IonCascadingConfig assumes some familiarity with the [Ion data format](https://amzn.github.io/ion-docs/docs/spec.html). If you aren't familiar with Ion, don't worry! Ion is a super-set of [JSON](https://en.wikipedia.org/wiki/JSON) with a few add-ons like first class support for dates, the ability to annotate data, s-expressions, binary serialization, and more. It is widely used within Amazon and pretty straightforward to pick up if you have some familiarity with JSON. The features of Ion that IonCascadingConfig uses that aren't in JSON are primarily [annotations](https://amzn.github.io/ion-docs/docs/spec.html#annot) so please review that small section if you aren't familiar with them.

### Choose where to store your config

The simplest and default way to store config is in Ion files (files with a `.ion` extension) in the `ion-cascading-config` folder at the root of your application's working directory. Only files that end with `.ion` will be used, all others will be ignored. For example, you could keep your config in a folder layout like this:
```
application-working-directory/ion-cascading-config
application-working-directory/ion-cascading-config/config1.ion
application-working-directory/ion-cascading-config/config2.ion
application-working-directory/ion-cascading-config/config3.ion
```

In this case all the config in all the files will be loaded together in a single IonConfigManager object. This is what happens when using `IonConfigManager.getInstance()`.

You may also choose to load your config in different ways such as from a different folder or from another service. See the "Loading config from other sources" section below for details.

### Define a namespace for your config.

A namespace groups related config together and helps to isolate your config from other config your dependencies have if they are loaded in the same IonConfigManager.

Let's imagine we're making config for some products we sell. We'll call our namespace `Products`.
* We define a namespace by creating an Ion struct with two annotations on it, the first is literally the word "`Namespace`" followed by the actual name of your namespace, in this case `Products`.
* The only settings necessary to be defined for a namespace are the criteria which the config ranks values by. This is done by the `prioritizedCriteria` field which is an Ion list.

Once you put all that together you get your namespace definition:

```js
Namespace::Products::{
    prioritizedCriteria: []
}
```

### Add config

Now that you have a namespace, let's make it useful by adding some config. All config values are just key-value pairs in structs annotated with your namespace name.

Let's imagine we need to configure whether categories of products are eligible for free shipping at standard speeds. We want to be nice to our customers and say that all products are eligible for free shipping so the config is basic and is just always `true`.

```js
Products::{
    freeStandardShipping: true
}
```

### Read config
Now we can read this config in our application. You can access it through an instance of `NamespacedIonConfigManager` which provides many convenience methods on top of the low-level `IonConfigManager` and is scoped to a single namespace ("Products" in the example above). Most applications should use the NamespacedIonConfigManager but may need to first create an IonConfigManager to load the config from a different location, see the "Loading config from other sources" section.

Using the NamespacedIonConfigManager you can read a value to a specified type. Here we specify it is a boolean by doing `asBoolean()`.

```java
import com.amazon.ionconfig.NamespacedIonConfigManager;

public class FreeShippingEligibilityChecker {
    // load a NamespacedIonConfigManager with namespace "Products"
    private final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Products");
    public boolean isEligibleForStandardShipping() {
        return configManager.asBoolean().findOrThrow("freeStandardShipping"); // returns true
    }
}
```

### Using Criteria

Later on we improve our shipping speeds and can now offer a faster 2-day shipping for most situations. Bulky products like furniture and televisions are not eligible and clothing items and shoes are only eligible in the continental United States.

We *could* express this by concatenating the category and shipping location in the config key, but this makes many duplicated entries and scales exponentially whenever new types of conditions are added.

**Don't do this!**
```js
Products::{
    freeStandardShipping: true,

    // free 2 day shipping eligibility
    free2DayShipping.Books.ContinentalUs: true,
    free2DayShipping.Books.Hawaii: true,
    free2DayShipping.Books.Alaska: true,
    free2DayShipping.Shoes.ContinentalUs: true,
    free2DayShipping.Shoes.Hawaii: false,
    free2DayShipping.Shoes.Alaska: false,
    free2DayShipping.Cameras.ContinentalUs: true,
    free2DayShipping.Cameras.Hawaii: true,
    free2DayShipping.Cameras.Alaska: true,
    free2DayShipping.Laptops.ContinentalUs: true,
    free2DayShipping.Laptops.Hawaii: true,
    free2DayShipping.Laptops.Alaska: true,
    free2DayShipping.Clothes.ContinentalUs: true,
    free2DayShipping.Clothes.Hawaii: false,
    free2DayShipping.Clothes.Alaska: false,
    free2DayShipping.Furniture.ContinentalUs: false,
    free2DayShipping.Furniture.Hawaii: false,
    free2DayShipping.Furniture.Alaska: false,
    free2DayShipping.Tvs.ContinentalUs: false, // DON'T DO THIS, this is a counter-example!
    free2DayShipping.Tvs.Hawaii: false,
    free2DayShipping.Tvs.Alaska: false
}
```
```java
import com.amazon.ionconfig.NamespacedIonConfigManager;

public class FreeShippingEligibilityChecker {
    private final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Products");
    public boolean isEligibleForStandardShipping() {
        return configManager.asBoolean().findOrThrow("freeStandardShipping"); // returns true
    }
    public boolean isEligibleFor2DayShipping(final String category, final String location) {
        // DON'T DO THIS, this is a counter-example!
        return configManager.asBoolean().findOrThrow("free2DayShipping." + category + "." + location);
    }
}
```

**Do this instead!**

Instead, we can start to make use of the criteria! We'll start by defining two criteria, `category` and `location` within our namespace. To do this we add them to the `prioritizedCritera` list in our namespace definition.

```js
Namespace::Products::{
    prioritizedCriteria:[
        category,
        location
    ]
}
```

The order they are defined in specifies their priority from least important to most important. A value with no criteria has the least importance and values with more than one criterion specified are more specific and have a higher importance. More important values override less important values.

If you're familiar with CSS, you can think of the ordering of the prioritizedCriteria like the [specificity of CSS](https://developer.mozilla.org/en-US/docs/Web/CSS/Specificity). In this example, `location` is more important than `category` in the same way that CSS ID selectors are more important than CSS Class selectors. For an example of this in action, check the "Algorithm - How IonCascadingConfig works under-the-hood" section.

To specify that a value needs specific criteria, we can wrap it in a struct where the key of that struct contains the criteria plus a dash `-` followed by the value that the criteria must meet.
```js
Products::{
    free2DayShipping: true, // defaults to true
    'category-Tvs': {
        free2DayShipping: false // if category is "Tvs" then override to false
    }
}
```
To specify an `and` condition between multiple criteria, we can continue to wrap the value deeper in more structs like this.
```js
Products::{
    free2DayShipping: true, // defaults to true
    'category-Shoes': {
        'location-Alaska': {
            free2DayShipping: false // but if category is "Shoes" and location is "Alaska" then override to false
        }
    }
}
```
To specify an `or` condition between multiple criteria, we start by wrapping a value in a struct normally, then we annotate the struct with all the additional or'd criteria.
```js
Products::{
    free2DayShipping: true, // defaults to true

    // The first condition is applied as a key ':' and the rest of the or'd conditions are applied as annotations to the
    // struct using '::'. We move the annotations to new lines for readability/maintainability if there are many.
    'category-Tvs':
    'category-Furniture'::
    'category-Medicine'::
    'category-SomeOtherCategory'::{
        free2DayShipping: false // but if category is "Tvs" or "Furniture" or "Medicine" or "SomeOtherCategory" then override to false
    }
}
```
To specify a `not` condition, specify the criteria normally then add a `!` to the beginning of the key.
```js
Products::{
    free2DayShipping: true, // defaults to true
    '!category-Tvs': {
        free2DayShipping: false // but if category is not "Tvs" then override to false
    }
}
```
Now using our criteria we can express the conditions more easily. Just a reminder of what the conditions we wanted to express were:

We can now offer a faster 2-day shipping for most situations. Bulky products like furniture and televisions are not eligible and clothing items and shoes are only eligible in the continental United States.
```js
Products::{
    freeStandardShipping: true,

    // free 2 day shipping eligibility
    free2DayShipping: true, // defaulting to true
    'category-Furniture':
    'category-Tvs'::{
        free2DayShipping: false // if category is "Tvs" or "Furniture" then override to false
    },
    'category-Shoes':
    'category-Clothes'::{
        free2DayShipping: false, // if category is "Shoes" or "Clothes" then override to false
        'location-ContinentalUs': {
            free2DayShipping: true // if category is "Shoes" or "Clothes" and location is "ContinentalUs" then override to true
        }
    }
}
```
```java
import com.amazon.ionconfig.NamespacedIonConfigManager;

public class FreeShippingEligibilityChecker {
    private final NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Products");
    public boolean isEligibleForStandardShipping() {
        return configManager.asBoolean().findOrThrow("freeStandardShipping"); // returns true
    }
    public boolean isEligibleFor2DayShipping(final String category, final String location) {
        return configManager.asBoolean()
            .withProperty("category", category)
            .withProperty("location", location)
            .findOrThrow("free2DayShipping");
    }
}
```

Hurray, the problem is solved! In the future when new requirements come up, we can add more criteria and rules as necessary.

# Advanced Features

Examples below assume a namespace defined as follows:
```js
Namespace::Products::{
    prioritizedCriteria:[
        websiteFeatureGroup,
        department,
        category,
        subcategory,
        sku
    ]
}
```
Lists and Structs of arbitrary depth may be dynamically created using criteria as well.

### Dynamic Structs and Lists

**Structs**

Dynamic structs can be defined as below, ordering within a dynamic struct is not guaranteed to be the same as the config.

```js
Products::{
    // when department is 107, result will be {b:10, a:1}
    // when department is 108, result will be {b:10, a:2}
    result: {
        b: 10,
        'department-107': {
            a: 1
        },
        'department-108': {
            a: 2
        }
    }
}
```

**Lists**

Dynamic values within a list must be contained within a struct that is annotated with at least one criteria, more can be added and they are treated with an **OR** relationship as normal. The struct can have arbitrary depth of criteria but must contain exactly one property. The property must either be a list named `values` or any type named `value`. If the criteria is met, the items in `values` will be inserted inline into the parent list in the same order as the config or if it is named `value`, it will be inserted into the list as is.

```js
Products::{
    // when department is 107, result will be [123, 456, 789, 999]
    // when department is 108, result will be [123, 12345, 999]
    result: [
        123,
        'department-107'::{
            values: [456, 789]
        },
        'department-108'::{
            value: 12345
        },
        999
    ]
}
```

Lists and structs may be combined with arbitrary depth and complexity.

```js
Products::{
    layout: [
        brand,
        title,
        customerReviews,
        {
            name: "price",
            template: "common",
            'websiteFeatureGroup-wireless': {
                template: "wireless" // override the standard template for wireless
            },
            modules: [
                "businessPricing",
                "rebates",
                "quantityPrice",
                "points",
                'department-111'::{
                    value: "globalStoreIfd"
                },
                'department-222'::{
                    value: "priceTax"
                },
                'department-333'::{
                    value: "promoMessaging"
                },
                'category-444'::'category-555'::{
                    'websiteFeatureGroup-wireless': {
                        values: [
                            {
                                name: "promoMessaging",
                                template: "common",
                                'subcategory-1234': {
                                    template: "customTemplate1"
                                },
                                'subcategory-2345': {
                                    template: "customTemplate2"
                                }
                            },
                            "samplingBuyBox"
                        ]
                    }
                }
            ]
        }
    ]
}
```
```
// Given websiteFeatureGroup=wireless, department=111, category=555, subcategory=1234 then layout will be:

layout: [
    brand,
    title,
    customerReviews,
    {
        name: "price",
        template: "wireless",
        modules: [
            "businessPricing",
            "rebates",
            "quantityPrice",
            "points",
            "globalStoreIfd",
            {
                name: "promoMessaging",
                template: "customTemplate1"
            },
            "samplingBuyBox"
        ]
    }
]
```

### NamespacedIonConfigManager Settings

If unspecified, NamespacedIonConfigManager will use the default IonConfigManager as described in the "Choose where to store your config" section above. There are several options which can be configured to change the default functionality. Some of these can be passed to the constructor or they can be set on a `NamespacedIonConfigManager.Options` object which can be passed in the constructor.
* `namespace` - The namespace to use.
* `defaultProperties` - Properties which are always applied to every query. Useful for specifying something once that is true for the rest of the application's runtime. An example could be an AWS region that the environment is running in or environment variables.
* `defaultPredicates` - Same as `defaultProperties` but with more flexible function predicates.
* `configManager` - The IonConfigManager to use for accessing config values. Will need to be passed in if the default instance is not being used.
* `ionMapper` - Jackson's IonObjectMapper to use. Should be passed in if there is custom configuration being applied to the IonObjectMapper. [Jackson-dataformat-ion](https://github.com/FasterXML/jackson-dataformats-binary/tree/2.12/ion)
* `queriesCacheResults` - Determines if multiple `find` calls to the same `Query` object created from this NamespacedIonConfigManager will re-evaluate the config criteria or not. Set to always re-evaluate the config criteria by default.

The NamespacedIonConfigManager has built in support for finding the following types:
* java.lang.String `asString()`
* java.lang.Integer `asInteger()`
* java.lang.Long `asLong()`
* java.lang.Double `asDouble()`
* java.lang.Boolean `asBoolean()`
* java.math.BigInteger `asBigInteger()`
* java.math.BigDecimal `asBigDecimal()`
* java.util.Date `asDate()`
* java.time.Instant `asInstant()`

### Reading Custom Classes/Types

Additionally, support for deserialization of custom classes and other types is allowed by making use of the Jackson-dataformat-ion library.
* Classes `asClass(Class)`
* Generic Types `asType(TypeReference)`

```js
Products::{
    'sku-B0000SKUU1': {
        extraDiscount: {
            title: "End of year discount!",
            amount: 3.50,
            currencyCode: "USD"
        },
        bulletPoints: [
            "Shiny",
            "Red",
            "Excellent Value"
        ]
    }
}
```

```java
// import lombok annotations to simplify necessary jackson code, not necessary for this library to work though
@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
private static class Discount {
    private final String title;
    private final BigDecimal amount;
    private final String currencyCode;
}

// ... NamespacedIonConfigManager is initialized as configManager ...

Optional<Discount> extraDiscount = configManager.asClass(Discount.class).withProperty("sku", "B0000SKUU1").find("extraDiscount");
Optional<List<String>> bulletPoints = configManager.asType(new TypeReference<List<String>>(){})
        .withProperty("sku", "B0000SKUU1")
        .find("bulletPoints");
```

### Custom Predicates

Custom predicates for criteria may be specified beyond the default equality check on both the IonConfigManager and NamespacedIonConfigManager. For instance, a criteria could be made to match a regex pattern, compare against a date, or call a service to get a value dynamically, like calling an API.

```js
Namespace::Products::{
    prioritizedCriteria:[
        websiteFeatureGroup,
        department,
        category,
        subcategory,
        sku,
        featureFlag // we will specify a custom predicate in Java
    ]
}

Products::{
    'sku-B0000SKUU1': {
        'featureFlag-MY_FLAG_12345:T1': { // only allow the discount if the flag is set to "T1"
            extraDiscount: {
                title: "End of year discount!",
                amount: 3.50,
                currencyCode: "USD"
            }
        }
    }
}
```

A CriteriaPredicate is an interface that takes a Set of Strings and returns true if any strings in the set pass the custom criteria. The Set of Strings comes from the fact that criteria may be **or**'d together, which produces a Set as an optimization, rather than checking each **or**'d string individually. The CriteriaPredicate class contains factory methods to simplify creating custom predicates.

```java
FeatureFlagClient featureFlagClient = new FeatureFlagClient();
NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Products");

Optional<Discount> extraDiscount = configManager.asClass(Discount.class)
        .withProperty("sku", "B0000SKUU1")
        .withPredicate("featureFlag", CriteriaPredicate.fromCondition(featureFlag -> { // featureFlag is "MY_FLAG_12345:T1"
            final String[] splitFeatureFlag = featureFlag.split(":");
            final String actualTreatment = Optional.ofNullable(featureFlagClient.getTreatmentTrigger(splitFeatureFlag[0])).orElse("C");
            return splitFeatureFlag[1].equals(actualTreatment);
        }))
        .find("extraDiscount");
```

### Loading config from other sources

You may also choose to load your config in different ways such as from a different folder (by using `IonCascadingConfig.fromDirectory(path)`) or from another service and passing the Ion data (using one of the `IonCascadingConfig.from*` methods) to the IonConfigManager.

For example, you could write your config in a file in AWS S3 or another location and load/parse it using the `IonJava` package then create an IonConfigManager out of it. If you want to periodically refresh your config, you can simply fetch the config again from your external data source and then recreate the IonConfigManager for use within your application.

Here's an example of loading the config from the "Getting Started" section if it were stored in AWS S3.

```java
class FreeShippingEligibilityChecker {
    private static final String S3_BUCKET = "example-bucket";
    private static final String S3_KEY = "example-config-entry.ion";
    private static final String CONFIG_NAME = S3_BUCKET + "/" + S3_KEY;
    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();
    private final NamespacedIonConfigManager configManager;

    public FreeShippingEligibilityChecker() {
        // create S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

        // call S3 for data
        try (S3Object object = s3Client.getObject(new GetObjectRequest(S3_BUCKET, S3_KEY));
            InputStream objectData = object.getObjectContent()) {

            // parse data into IonDatagram
            IonDatagram ionDatagram = ION_SYSTEM.getLoader().load(ION_SYSTEM.newReader(objectData));

            // create NamespacedIonConfigManager from IonDatagram
            IonConfigManager ionConfigManager = IonConfigManager.fromDatagram(CONFIG_NAME, ionDatagram);
            configManager = new NamespacedIonConfigManager(NamespacedIonConfigManager.Options.builder()
                    .namespace("Products")
                    .configManager(ionConfigManager)
                    .build());
        }
    }

    public boolean isEligibleForStandard() {
        return configManager.asBoolean().findOrThrow("freeStandardShipping"); // returns true
    }
}
```

To load config from a folder other than `/ion-cascading-config` use `IonCascadingConfig.fromDirectory(path)`.

### Using Queries

To look up data for different types with the same properties, Query objects may be converted to different types by calling the relevant `as*` method such as `asString()` or `asBoolean()`. The properties and internal state is shared between all copied Query objects, updating the property of one will update them all. This does not affect new Query objects created by the NamespacedIonConfigManager which get a new internal state.

```java
// ... NamespacedIonConfigManager is initialized as configManager ...
Query<Discount> query = configManager.asClass(Discount.class).withProperty("sku", "B0000SKUU1");

// all of these calls find the keys using sku-B0000SKUU1
Optional<Discount> extraDiscount = query.find("extraDiscount");
Optional<List<String>> bulletPoints = query.asType(new TypeReference<List<String>>(){}).find("bulletPoints");
String myString = query.asString().findOrThrow("myString");

// This Query has does not use sku-B0000SKUU1 because it is a new Query object from the configManager.
Query<String> stringQuery = configManager.asString();
```

If you simply want to get all the fields from the config that match your properties and predicates, you may call the `Query.findAll()` method. This returns a `Map<String, IonValue>` of every value that matched and its corresponding raw IonValue.

```java
// ... NamespacedIonConfigManager is initialized as configManager ...
Map<String, IonValue> values = configManager.asIon().withProperty("sku", "B0000SKUU1").findAll();
```

By default, each call to a `find*` method on a Query will evaluate the config for the specified properties. It doesn't re-parse the config but does walk through the config data structure in memory applying the rules. You may enable your queries to keep the last fetched value in memory and simply reuse those values in subsequent calls so the config data structure is not evaluated again. If the properties change between calls to `find` methods, the last value stored in memory is removed and the config will be walked through again.

```java
// ... NamespacedIonConfigManager is initialized as configManager ...
Query<Discount> query = configManager.asClass(Discount.class)
        .withProperty("sku", "B0000SKUU1")
        .cacheResults(); // sets the query to keep the results of find calls in memory and reuse them

// the config is evaluated here
Optional<Discount> extraDiscount = query.find("extraDiscount");

// the config from the last find call is reused for these calls since properties are not changing
Optional<List<String>> bulletPoints = query.asType(new TypeReference<List<String>>(){}).find("bulletPoints");
String myString = query.asString().findOrThrow("myString");

// You may also specify a default value in the NamespacedIonConfigManager for queries to be cached or not.
Options configManagerOptions = Options.builder()
        .queriesCacheResults(true)
        .namespace("Products")
        .build()
NamespacedIonConfigManager cachedConfigManager = new NamespacedIonCascadingConfigManager(configManagerOptions);
// cachedQuery starts the same as having done configManager.asString().cacheResults();
Query<String> cachedQuery = cachedConfigManager.asString();

// You may disable caching for an individual query if you don't want the default settings from the config manager
Query<String> query = cachedConfigManager.asString().doNotCacheResults(); // direct method
Query<String> query = cachedConfigManager.asString().cacheResults(false); // alternate method to disable caching if you want to pass this setting as a variable dynamically
```

**NOTE** When using custom predicates, if your custom predicate is expected to change frequently, you may not want to use them with the cached Queries because the config will not be re-evaluated on subsequent `find` calls. For these cases you can choose not to use the cached setting or just recreate the query object each time. See the example below.

```java
public class DiscountConfig {

    private final Query<Discount> discountQuery;

    public DiscountConfig() {
        FeatureFlagClient featureFlagClient = new FeatureFlagClient();
        NamespacedIonConfigManager configManager = new NamespacedIonConfigManager("Products");

        Query<Discount> query = configManager.asClass(Discount.class)
                .withProperty("sku", "B0000SKUU1")
                .withPredicate("featureFlag", CriteriaPredicate.fromCondition(featureFlag -> { // featureFlag is "MY_FLAG_12345:T1"
                    final String[] splitfeatureFlag = featureFlag.split(":");
                    final String actualTreatment = Optional.ofNullable(featureFlagClient.getTreatmentTrigger(splitfeatureFlag[0])).orElse("C");
                    return splitfeatureFlag[1].equals(actualTreatment);
                }))
                .cacheResults(); // enable query caching
    }

    public Discount getExtraDiscount() {
        return query.findOrThrow("extraDiscount");
    }

}
```
```java
DiscountConfig config = new DiscountConfig();

// returns some result
config.getExtraDiscount();

// hours later we disable a featureFlag guarding our discount

// doesn't recheck featureFlag and returns the same result!
config.getExtraDiscount();

```

# Algorithm - How IonCascadingConfig works under-the-hood

Example configuration
```js
Namespace::Products::{
    prioritizedCriteria:[
        category,
        seller,
        sku
    ]
}
Products::{
    "myValue": 1, // default
    "seller-1234": {
        "myValue": 2,
        "category-001234321": {
            "myValue": 3,
        }
    },
    "category-001234321": {
        "myValue": 4,
        "sku-B0000SKUU1": {
            "myValue": 5,
        },
        "sku-B0000SKUU2": {
            "myValue": 6,
        }
    }
}
```

Walking through this config and reading in the settings one by one from top to bottom (depth-first-search if you are thinking about this config as a tree)
```
Criteria                            myValue

default:                            1
seller-1234:                        2
seller-1234,category-001234321:     3
category-001234321:                 4
category-001234321,sku-B0000SKUU1:  5
category-001234321,sku-B0000SKUU2:  6
```

Sort each key's individual attributes by priority (highest to least priority):
```
Criteria                            myValue

default:                            1
seller-1234:                        2
seller-1234,category-001234321:     3
category-001234321:                 4
sku-B0000SKUU1,category-001234321:  5 // reorder sku and category
sku-B0000SKUU2,category-001234321:  6 // reorder sku and category
```

Sort all keys together by priority (least to highest priority) (equivilent to sorting a list of strings, each key would be just like a string and each key attribute would be just like a character of a string):
```
Criteria                            myValue

default:                            1
category-001234321:                 4 // category is moved to the top since it has the least total priority (category < seller)
seller-1234:                        2 // seller < seller + category
seller-1234,category-001234321:     3 // seller < sku
sku-B0000SKUU1,category-001234321:  5
sku-B0000SKUU2,category-001234321:  6
```

To process the example sku above, go through each entry in the configuration in order, skipping inapplicable settings, overriding fields as we go
properties: sku: B0000SKUU1, category: 001234321, seller: 1234

```
Criteria                            myValue     result

default:                            1           1
category-001234321:                 4           4
seller-1234:                        2           2
seller-1234,category-001234321:     3           3
sku-B0000SKUU1,category-001234321:  5           5
sku-B0000SKUU2,category-001234321:  6           5 // skip since sku-B0000SKUU2 is not applicable
```
