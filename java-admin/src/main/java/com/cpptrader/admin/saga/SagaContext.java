package com.cpptrader.admin.saga;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SagaContext {

    private final String sagaId;
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public SagaContext(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getSagaId() { return sagaId; }

    public void put(String key, Object value) { data.put(key, value); }
    public Object get(String key) { return data.get(key); }
    public <T> T get(String key, Class<T> type) { return type.cast(data.get(key)); }
    public Map<String, Object> getData() { return new HashMap<>(data); }
}
