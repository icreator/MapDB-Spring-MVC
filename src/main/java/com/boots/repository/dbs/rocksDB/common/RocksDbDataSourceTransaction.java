package com.boots.repository.dbs.rocksDB.common;

import lombok.extern.slf4j.Slf4j;
import org.erachain.dbs.Transacted;
import org.erachain.dbs.TransactedThrows;
import org.erachain.dbs.rocksDB.indexes.IndexDB;
import org.rocksdb.*;

import java.util.List;

/**
 * База данных RocksDB с поддержкой транзакционной модели.
 * Причем сама база не делает commit & rollback. Для этого нужно отдельно создавать Транзакцию
 */
@Slf4j
public class RocksDbDataSourceTransaction extends RocksDbDataSourceImpl implements Transacted {

    ReadOptions readOptions;
    WriteOptions writeOptions;

    public RocksDbDataSourceTransaction(String pathName, String name, List<IndexDB> indexes,
                                        TransactionDB dbCore, List<ColumnFamilyHandle> columnFamilyHandles,
                                        WriteOptions writeOptions, ReadOptions readOptions, boolean enableSize) {
        super(pathName, name, indexes, null, enableSize);
        this.alive = true;
        this.dbCore = dbCore;
        this.columnFamilyHandles = columnFamilyHandles;
        this.readOptions = readOptions;
        this.writeOptions = writeOptions;

        table = new RocksDbComTransaction(dbCore, writeOptions, readOptions);
        afterOpenTable();

    }

    public RocksDbDataSourceTransaction(String pathName, String name, List<IndexDB> indexes,
                                        TransactionDB dbCore, List<ColumnFamilyHandle> columnFamilyHandles, boolean enableSize) {
        this(pathName, name, indexes, dbCore, columnFamilyHandles,
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(), enableSize);
    }

    @Override
    protected void createDB(Options options, List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws RocksDBException {
        return;
    }

    @Override
    protected void openDB(DBOptions dbOptions, List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws RocksDBException {
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
