package org.cloudServices.infra.computeUnit;

public enum ComputeUnitCapacity {
    MICRO(1),
    MEDIUM(3),
    LARGE(5),
    ULTRA(10);

    private final int slots;

    ComputeUnitCapacity(int slots) {
        this.slots = slots;
    }

    public int getSlots() {
        return slots;
    }
}
