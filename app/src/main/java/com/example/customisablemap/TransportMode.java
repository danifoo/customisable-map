package com.example.customisablemap;

public enum TransportMode {
    BUS("BUS"),
    BIKE("BICYCLE"),
    TRAIN("TRAIN"),
    WALKING("WALKING");

    private final String apiMode;

    TransportMode(String apiMode) {
        this.apiMode = apiMode;
    }

    public String getApiMode() {
        return apiMode;
    }
}