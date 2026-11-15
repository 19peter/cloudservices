package org.cloudServices.builder;

import org.cloudServices.global.db.Memdb;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.infra.computeUnit.ComputeUnitCapacity;
import org.cloudServices.infra.runtimes.Workload;

public class ComputeUnitBuilder {
    private Workload workload;
    private String name;
    private ComputeUnitCapacity capacity = ComputeUnitCapacity.MICRO;

    public ComputeUnitBuilder workload(Workload workload) {
        this.workload = workload;
        return this;
    }

    public ComputeUnitBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ComputeUnitBuilder capacity(ComputeUnitCapacity capacity) {
        this.capacity = capacity;
        return this;
    }

    public ComputeUnit build() {
        ComputeUnit computeUnit = new ComputeUnit(workload, name, capacity.getSlots());
        Memdb.register(computeUnit);
        return computeUnit;
    }
}
