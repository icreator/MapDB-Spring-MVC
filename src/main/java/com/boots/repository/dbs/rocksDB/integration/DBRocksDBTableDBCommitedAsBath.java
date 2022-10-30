package com.boots.repository.dbs.rocksDB.integration;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.ReadOptions;
import org.rocksdb.WriteOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Данный класс представляет собой основной доступ и функционал к таблице БД RocksDB
 * Тут происходит обработка настроенных вторичных индексов.
 * вызывается из SUIT
 *
 * @param <K>
 * @param <V>
 */
@Slf4j
public class DBRocksDBTableDBCommitedAsBath<K, V> extends DBRocksDBTable<K, V>
        implements Transacted {

    ReadOptions readOptions;

    public DBRocksDBTableDBCommitedAsBath(Byteable byteableKey, Byteable byteableValue, String NAME_TABLE, List<IndexDB> indexes,
                                          RocksDbSettings settings, WriteOptions writeOptions, ReadOptions readOptions, DBASet dbaSet, boolean enableSize) {
        super(byteableKey, byteableValue, NAME_TABLE, indexes, settings, writeOptions, dbaSet, enableSize);
        this.readOptions = readOptions;
        openSource();
        afterOpen();
    }

    public DBRocksDBTableDBCommitedAsBath(Byteable byteableKey, Byteable byteableValue, List<IndexDB> indexes,
                                          RocksDbSettings settings, WriteOptions writeOptions, ReadOptions readOptions, DBASet dbaSet, boolean enableSize) {
        super(byteableKey, byteableValue, indexes, settings, writeOptions, enableSize);
        this.readOptions = readOptions;
        openSource();
        afterOpen();
    }

    public DBRocksDBTableDBCommitedAsBath(String NAME_TABLE, boolean enableSize) {
        this(new ByteableTrivial(), new ByteableTrivial(), NAME_TABLE,
                new ArrayList<>(), RocksDbSettings.getDefaultSettings(),
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(), null, enableSize);
    }

    @Override
    public void openSource() {
        dbSource = new RocksDbDataSourceDBCommitAsBath(this.root, NAME_TABLE, indexes, settings, writeOptions, readOptions, enableSize);

    }

    @Override
    public void commit() {
        ((Transacted) dbSource).commit();
    }

    @Override
    public void rollback() {
        ((Transacted) dbSource).rollback();
    }
}
