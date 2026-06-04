package ru.spb.reshenie.chekerstatus.sync.service;

import java.util.Collections;
import java.util.Map;

public interface LiveUpdatePublisher {

    void publish(String type, Map<String, Object> payload);

    default void publish(String type) {
        publish(type, Collections.emptyMap());
    }
}
