package org.ulpgc.dacd.domain;

public interface EventPublisher extends AutoCloseable {
    void publish(String topicName, EventMessage eventMessage);

    @Override
    void close();
}
