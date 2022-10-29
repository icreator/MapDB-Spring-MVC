package org.erachain.dbs.rocksDB.integration;

import lombok.extern.slf4j.Slf4j;
import org.erachain.database.DBASet;
import org.erachain.dbs.rocksDB.common.RocksDbDataSourceDB;
import org.erachain.dbs.rocksDB.common.RocksDbSettings;
import org.erachain.dbs.rocksDB.indexes.IndexDB;
import org.erachain.dbs.rocksDB.transformation.Byteable;
import org.erachain.dbs.rocksDB.transformation.ByteableTrivial;
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
public class DBRocksDBTableDB<K, V> extends DBRocksDBTable<K, V> {

    public DBRocksDBTableDB(Byteable byteableKey, Byteable byteableValue, String NAME_TABLE, List<IndexDB> indexes,
                            RocksDbSettings settings, WriteOptions writeOptions, DBASet dbaSet, boolean enableSize) {
        super(byteableKey, byteableValue, NAME_TABLE, indexes, settings, writeOptions, dbaSet, enableSize);
        openSource();
        afterOpen();
    }

    public DBRocksDBTableDB(Byteable byteableKey, Byteable byteableValue, List<IndexDB> indexes,
                            RocksDbSettings settings, WriteOptions writeOptions, boolean enableSize) {
        super(byteableKey, byteableValue, indexes, settings, writeOptions, enableSize);
        openSource();
        afterOpen();
    }

    public DBRocksDBTableDB(String NAME_TABLE, boolean enableSize) {
        this(new ByteableTrivial(), new ByteableTrivial(), NAME_TABLE,
                new ArrayList<>(), RocksDbSettings.getDefaultSettings(),
                new WriteOptions().setSync(true).setDisableWAL(false), null, enableSize);
    }

    @Override
    public void openSource() {
        dbSource = new RocksDbDataSourceDB(this.root, NAME_TABLE, indexes, settings, writeOptions, enableSize);
    }
}
