package com.xabber.domain.entity;

public interface Cache<K, V> {

    /**
     * Put a value in the cache.
     *
     * @param key the key of the value.
     * @param value the value.
     * @return the previous value or {@code null}.
     */
    V put(K key, V value);

    /**
     * Returns the value of the specified key, or {@code null}.
     *
     * @param key the key.
     * @return the value found in the cache, or {@code null}.
     * @deprecated Use {@link #lookup(Object)} instead.
     */
    @Deprecated
    V get(Object key);

    /**
     * Returns the value of the specified key, or {@code null}.
     *
     * @param key the key.
     * @return the value found in the cache, or {@code null}.
     */
    V lookup(K key);

    /**
     * Return the maximum cache Size.
     *
     * @return the maximum cache size.
     */
    int getMaxCacheSize();

    /**
     * Set the maximum cache size.
     * @param size the new maximum cache size.
     */
    void setMaxCacheSize(int size);
}

