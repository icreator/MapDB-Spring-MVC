package com.boots.repository.dbs.rocksDB.integration;

import lombok.extern.slf4j.Slf4j;
import org.erachain.database.DBASet;
import org.erachain.dbs.Transacted;
import org.erachain.dbs.rocksDB.common.RocksDbDataSourceOptTransaction;
import org.erachain.dbs.rocksDB.common.RocksDbSettings;
import org.erachain.dbs.rocksDB.indexes.IndexDB;
import org.erachain.dbs.rocksDB.transformation.Byteable;
import org.erachain.dbs.rocksDB.transformation.ByteableTrivial;
import org.rocksdb.OptimisticTransactionDB;
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
public class DBRocksDBTableOptTransaction<K, V> extends DBRocksDBTable<K, V> implements Transacted {

    ReadOptions readOptions;

    public DBRocksDBTableOptTransaction(Byteable byteableKey, Byteable byteableValue, String NAME_TABLE,
                                        List<IndexDB> indexes,
                                        DBRocksDBTableDBOptTransacted dbSoutceImpl, RocksDbSettings settings,
                                        WriteOptions writeOptions, ReadOptions readOptions, DBASet dbaSet, boolean enableSize) {
        super(byteableKey, byteableValue, NAME_TABLE, indexes, settings, writeOptions, dbaSet, enableSize);
        this.readOptions = readOptions;
        this.dbSource = new RocksDbDataSourceOptTransaction(this.root, NAME_TABLE, indexes,
                (OptimisticTransactionDB) dbSoutceImpl.dbSource, dbSoutceImpl.dbSource.getColumnFamilyHandles(), writeOptions, readOptions, this.enableSize);
        //openSource();
        afterOpen();
    }

    public DBRocksDBTableOptTransaction(Byteable byteableKey, Byteable byteableValue, String NAME_TABLE, List<IndexDB> indexes,
                                        DBRocksDBTableDBOptTransacted dbSource, DBASet dbaSet, boolean enableSize) {
        this(byteableKey, byteableValue, NAME_TABLE, indexes, dbSource, RocksDbSettings.getDefaultSettings(),
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(), dbaSet, enableSize);
    }

    public DBRocksDBTableOptTransaction(String NAME_TABLE, DBRocksDBTableDBOptTransacted dbSource, boolean enableSize) {
        this(new ByteableTrivial(), new ByteableTrivial(), NAME_TABLE,
                new ArrayList<>(), dbSource, RocksDbSettings.getDefaultSettings(),
                new WriteOptions().setSync(true).setDisableWAL(false), new ReadOptions(), null, enableSize);
    }

    @Override
    public void openSource() {
    }

    public void commit() {
        ((Transacted) dbSource).commit();
    }

    public void rollback() {
        ((Transacted) dbSource).rollback();
    }

    public void close() {
        dbSource.close();
    }
}
