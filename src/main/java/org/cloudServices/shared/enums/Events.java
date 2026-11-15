package org.cloudServices.shared.enums;

public enum Events {
    RUNTIME_CREATED,

    //COMPUTE UNIT EVENTS
    COMPUTE_UNIT_RESERVED,
    COMPUTE_UNIT_RUNNING,
    COMPUTE_UNIT_KILLED,

    //HEALTH EVENTS
    //COMPUTE_UNIT_RUNNING units could be either Ready or not (Unhealthy)
    READY,
    UNHEALTHY,

}
