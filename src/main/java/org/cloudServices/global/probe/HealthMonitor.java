package org.cloudServices.global.probe;

public class HealthMonitor {
    //Checks Liveness and Readiness
    //Liveness -> when a workload is submitted
    //Readiness -> when ServiceEndpoint in RuntimeSubmission returns a response from actuator /health
    //HealthMonitor publishes events as STARTING, READY, FAILED, UNHEALTHY and STOPPING
    //with ServiceIdentity and ServiceEndpoint

    //HealthMonitor should check periodically all running instances. Assign Executor Threads to check
    //all instances
}
