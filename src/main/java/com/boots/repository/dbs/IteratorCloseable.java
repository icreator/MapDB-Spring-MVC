package com.boots.repository.dbs;

import java.io.Closeable;
import java.util.Iterator;

/**
 *
 * @param <T>
 */
public interface IteratorCloseable<T> extends Iterator<T>, Closeable {
}
