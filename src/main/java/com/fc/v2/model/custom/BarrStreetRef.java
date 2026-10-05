package com.fc.v2.model.custom;

/**
 * 底册属地落到街道的结果（街代号 + 街名）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BarrStreetRef {

    private final String streetCode;
    private final String streetName;

    public BarrStreetRef(String streetCode, String streetName) {
        this.streetCode = streetCode;
        this.streetName = streetName;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public String getStreetName() {
        return streetName;
    }
}
