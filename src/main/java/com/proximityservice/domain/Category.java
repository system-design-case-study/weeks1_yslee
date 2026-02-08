package com.proximityservice.domain;

import java.util.Arrays;

public enum Category {

    KOREAN_FOOD("korean_food"),
    CHINESE_FOOD("chinese_food"),
    JAPANESE_FOOD("japanese_food"),
    WESTERN_FOOD("western_food"),
    CAFE("cafe"),
    BAR("bar"),
    CONVENIENCE("convenience"),
    PHARMACY("pharmacy"),
    HAIR_SALON("hair_salon"),
    GYM("gym");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Category fromValue(String value) {
        return Arrays.stream(values())
                .filter(c -> c.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid category: '" + value + "'. Allowed values: " +
                                Arrays.toString(Arrays.stream(values()).map(Category::getValue).toArray())
                ));
    }
}
