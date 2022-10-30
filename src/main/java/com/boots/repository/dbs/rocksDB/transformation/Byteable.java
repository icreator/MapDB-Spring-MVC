package com.boots.repository.dbs.rocksDB.transformation;

public interface Byteable<T> {

    T receiveObjectFromBytes(byte[] bytes);

    byte[] toBytesObject(T value);
}