package com.amazon.ionconfig.example;

import com.amazon.ionconfig.NamespacedIonConfigManager;

public class Main {
    public static void main(String[] args) {
        final NamespacedIonConfigManager namespacedIonConfigManager = new NamespacedIonConfigManager(
                NamespacedIonConfigManager.Options.builder().namespace("exampleNamespace").build()
        );

        System.out.println("Default value from config: " + namespacedIonConfigManager.asInteger().findOrThrow("myValue"));
        System.out.println("Value from config for domain 'prod': " + namespacedIonConfigManager.asInteger().withProperty("domain", "prod").findOrThrow("myValue"));
    }
}