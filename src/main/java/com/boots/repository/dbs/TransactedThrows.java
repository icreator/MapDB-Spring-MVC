package com.boots.repository.dbs;

import org.rocksdb.RocksDBException;

public interface TransactedThrows {

    void commit() throws RocksDBException;

    void rollback() throws RocksDBException;
}
