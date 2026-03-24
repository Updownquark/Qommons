package org.qommons;

import java.util.function.Supplier;

/**
 * A resource retrieved from a cache, which should be returned via {@link #close()} when no longer needed.
 * 
 * @param <T> The type of the resource
 */
public interface Resource<T> extends Supplier<T>, Transaction {
}