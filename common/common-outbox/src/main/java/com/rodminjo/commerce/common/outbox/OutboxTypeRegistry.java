package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.Descriptors.Descriptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OutboxTypeRegistry {

    private final ConcurrentMap<String, Descriptor> registry = new ConcurrentHashMap<>();

    public void register(Descriptor descriptor) {
        registry.put(descriptor.getFullName(), descriptor);
    }

    public Descriptor get(String fullName) {
        Descriptor descriptor = registry.get(fullName);
        if (descriptor == null) {
            throw new IllegalStateException("No descriptor registered for eventType: " + fullName);
        }
        return descriptor;
    }
}
