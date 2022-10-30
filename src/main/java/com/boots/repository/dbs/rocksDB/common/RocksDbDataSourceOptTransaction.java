package com.boots.repository.dbs.rocksDB.common;

import lombok.extern.slf4j.Slf4j;
import org.erachain.dbs.Transacted;
import org.erachain.dbs.TransactedThrows;
import org.erachain.dbs.rocksDB.indexes.IndexDB;
import org.erachain.settings.Settings;
import org.rocksdb.*;

import java.util.ArrayList;
import java.util.List;

import static org.erachain.dbs.rocksDB.utils.ConstantsRocksDB.ROCKS_DB_FOLDER;

/**
 * База данных RocksDB с поддержкой транзакционной модели.
 * Причем сама база не делает commit & rollback. Для этого нужно отдельно создавать Транзакцию
 */
@Slf4j
public class RocksDbDataSourceOptTransaction extends RocksDbDataSourceImpl implements Transacted {

    ReadOptions readOptions;
    WriteOptions writeOptions;

    public RocksDbDataSourceOptTransaction(String pathName, String name, List<IndexDB> indexes,
                                           OptimisticTransactionDB dbCore, List<ColumnFamilyHandle> columnFamilyHandles,
                                           WriteOptions writeOptions, ReadOptions readOptions, boolean enableSize) {
        super(pathName, name, indexes, null, enableSize);
        this.alive = true;
        this.dbCore = dbCore;
        this.columnFamilyHandles = columnFamilyHandles;
        this.readOptions = readOptions;
        this.writeOptions = writeOptions;

        this.table = new RocksDbComOptTransaction(dbCore, writeOptions, readOptions);
        afterOpenTable();

    }

    public RocksDbDataSourceOptTransaction(String name, List<IndexDB> indexes,
                                           OptimisticTransactionDB dbCore, List<ColumnFamilyHandle> columnFamilyHandles, boolean enableSize) {
        this(Settings.getInstance().getDataChainPath() + ROCKS_DB_FOLDER, name, indexes, dbCore, columnFamilyHandles,
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(), enableSize);
    }

    public RocksDbDataSourceOptTransaction(String name, OptimisticTransactionDB dbCore, List<ColumnFamilyHandle> columnFamilyHandles, boolean enableSize) {
        this(name, new ArrayList<>(), dbCore, columnFamilyHandles, enableSize);
    }

    //public RocksDbDataSourceOptTransaction(DBRocksDBTable dbSource) {
    //    this(dbSource., name, indexes, dbCore, columnFamilyHandles,
    //            new WriteOptions().setSync(true).setDisableWAL(false),
    //            new ReadOptions());
    //}

    @Override
    protected void createDB(Options options, List<ColumnFamilyDescriptor> columnFamilyDescriptors) {
        return;
    }

    @Override
    protected void openDB(DBOptions dbOptions, List<ColumnFamilyDescriptor> columnFamilyDescriptors) {
        return;
    }

    @Override
    public void afterOpenTable() {
        if (false && enableSize) {
            columnFamilyFieldSize = columnFamilyHandles.get(columnFamilyHandles.size() - 1);
        }
    }

    @Override
    public void commit() {
        resetDbLock.writeLock().lock();
        try {
            ((TransactedThrows) table).commit();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public void rollback() {
        resetDbLock.writeLock().lock();
        try {
            ((TransactedThrows) table).rollback();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    /**
     * Close only Transaction - not parentDB
     */
    @Override
    public void close() {
        resetDbLock.writeLock().lock();
        try {
            commit();
            table.close();
        } catch (Exception e) {
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

}
