package org.cloudServices.infra.runtimes;

public interface RuntimeInterface {
    void boot();
    String submit();
    void stop();
}
