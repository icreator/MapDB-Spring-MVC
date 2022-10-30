package com.boots.repository.dbs.rocksDB;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class IteratorCloseableImpl<K> implements IteratorCloseable<K> {

    private DBIterator iterator;
    private Byteable byteableKey;
    private boolean isClosed;


    public IteratorCloseableImpl(DBIterator iterator, Byteable byteableKey) {
        this.iterator = iterator;
        this.byteableKey = byteableKey;
    }

    /// нужно обязательно освобождать память, см https://github.com/facebook/rocksdb/wiki/RocksJava-Basics
    @Override
    public void close() {
        try {
            iterator.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        isClosed = true;
    }

    @Override
    public void finalize() throws Throwable {
        if (!isClosed) {
            close();
            logger.warn("FINALIZE used");
        }
        super.finalize();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public K next() {
        return (K) byteableKey.receiveObjectFromBytes(iterator.next());
    }

}
