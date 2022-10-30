package com.boots.repository.dbs.rocksDB.indexes;


public interface IndexByteable<R, K> {
    byte[] toBytes(R result);
}
