package org.erachain.dbs.rocksDB.comm;

import lombok.extern.slf4j.Slf4j;
import org.erachain.core.BlockChain;
import org.erachain.dbs.Transacted;
import org.erachain.dbs.rocksDB.common.RocksDbSettings;
import org.erachain.dbs.rocksDB.indexes.IndexDB;
import org.erachain.settings.Settings;
import org.rocksdb.*;

import java.util.List;

import static org.erachain.dbs.rocksDB.utils.ConstantsRocksDB.ROCKS_DB_FOLDER;

/**
 * Транзакции с помощью пакета и нативные методы с пакетом типа writeBatch.getFromBatchAndDB
 */
@Slf4j
public class RocksDbDataSourceDBCommitAsBath extends RocksDbDataSourceImpl implements Transacted {

    ReadOptions readOptions;

    protected WriteBatchWithIndex writeBatch;

    public RocksDbDataSourceDBCommitAsBath(String pathName, String name, List<IndexDB> indexes, RocksDbSettings settings,
                                           WriteOptions writeOptions, ReadOptions readOptions, boolean enableSize) {
        super(pathName, name, indexes, settings, writeOptions, enableSize);
        this.readOptions = readOptions;

        // Создаем или открываем ДБ
        initDB();
    }

    public RocksDbDataSourceDBCommitAsBath(String name, List<IndexDB> indexes, RocksDbSettings settings, boolean enableSize) {
        this(Settings.getInstance().getDataChainPath() + ROCKS_DB_FOLDER, name, indexes, settings,
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(), enableSize);
    }

    @Override
    protected void createDB(Options options, List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws RocksDBException {
        dbCore = RocksDB.open(options, getDbPathAndFile().toString());
        writeBatch = new WriteBatchWithIndex(true);
        dbOptions = new DBOptions(options);
    }

    @Override
    protected void openDB(DBOptions dbOptions, List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws RocksDBException {
        dbCore = RocksDB.open(dbOptions, getDbPathAndFile().toString(), columnFamilyDescriptors, columnFamilyHandles);
        writeBatch = new WriteBatchWithIndex(true);
        this.dbOptions = dbOptions;
    }

    @Override
    public void put(byte[] key, byte[] value) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.put(key, value);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void put(ColumnFamilyHandle columnFamilyHandle, byte[] key, byte[] value) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.put(columnFamilyHandle, key, value);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } catch (Exception e) {
            logger.error(this.getClass().getSimpleName() + " : " + columnFamilyHandle.toString());
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(byte[] key) {
        if (quitIfNotAlive()) {
            return false;
        }
        resetDbLock.readLock().lock();
        try {
            if (true) {
                return writeBatch.getFromBatchAndDB(dbCore, optionsReadDBcont, key) != null;
            } else {
                // так нельзя так как тут не будет учета удаленных в writeBatch

                // быстрая проверка - потенциально он может содержаться в базе?
                if (!dbCore.keyMayExist(key, inCache)) {
                    // тогда еще пакет проверим
                    return writeBatch.getFromBatch(optionsDBcont, key) != null;
                }

                if (BlockChain.CHECK_BUGS > 5) {
                    // проверка правильности поиска
                    boolean found = writeBatch.getFromBatch(optionsDBcont, key) != null;
                    if (found)
                        return true;
                    int valueFound = dbCore.get(optionsReadDBcont, key, containsBuff);
                    return valueFound != RocksDB.NOT_FOUND;

                } else {
                    // возможно что есть, проверим сначала в пакете
                    return writeBatch.getFromBatch(optionsDBcont, key) != null ||
                            ////dbCore.get(key, containsBuff) != RocksDB.NOT_FOUND;
                            /// быстрый поиск без получения данных
                            dbCore.get(optionsReadDBcont, key, containsBuff) != RocksDB.NOT_FOUND;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
        return false;
    }

    @Override
    public boolean contains(ColumnFamilyHandle columnFamilyHandle, byte[] key) {
        if (quitIfNotAlive()) {
            return false;
        }
        resetDbLock.readLock().lock();
        try {
            if (true) {
                return writeBatch.getFromBatchAndDB(dbCore, columnFamilyHandle, optionsReadDBcont, key) != null;
            } else {
                // так нельзя так как тут не будет учета удаленных в writeBatch
                // быстрая проверка - потенциально он может содержаться в базе?
                if (!dbCore.keyMayExist(columnFamilyHandle, key, inCache)) {
                    // тогда еще пакет проверим
                    return writeBatch.getFromBatch(columnFamilyHandle, optionsDBcont, key) != null;
                }

                // возможность что есть, все равно проверим
                return writeBatch.getFromBatch(columnFamilyHandle, optionsDBcont, key) != null ||
                        /// быстрый поиск без получения данных
                        dbCore.get(columnFamilyHandle, optionsReadDBcont, key, containsBuff) != RocksDB.NOT_FOUND;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
        return false;
    }

    @Override
    public byte[] get(byte[] key) {
        if (quitIfNotAlive()) {
            return null;
        }
        resetDbLock.readLock().lock();
        try {
            return writeBatch.getFromBatchAndDB(dbCore, readOptions, key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    // USE readOptions
    public byte[] get(final ReadOptions readOptions, final byte[] key) {
        if (quitIfNotAlive()) {
            return null;
        }
        resetDbLock.readLock().lock();
        try {
            return writeBatch.getFromBatchAndDB(dbCore, readOptions, key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public byte[] get(ColumnFamilyHandle columnFamilyHandle, byte[] key) {
        if (quitIfNotAlive()) {
            return null;
        }
        resetDbLock.readLock().lock();
        try {
            return writeBatch.getFromBatchAndDB(dbCore, columnFamilyHandle, readOptions, key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public byte[] get(ColumnFamilyHandle columnFamilyHandle, final ReadOptions readOptions, byte[] key) {
        if (quitIfNotAlive()) {
            return null;
        }
        resetDbLock.readLock().lock();
        try {
            return writeBatch.getFromBatchAndDB(dbCore, columnFamilyHandle, readOptions, key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.delete(key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void delete(ColumnFamilyHandle columnFamilyHandle, byte[] key) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.delete(columnFamilyHandle, key);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void deleteRange(byte[] keyFrom, byte[] keyToExclude) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.deleteRange(keyFrom, keyToExclude);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void deleteRange(ColumnFamilyHandle columnFamilyHandle, byte[] keyFrom, byte[] keyToExclude) {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            writeBatch.deleteRange(columnFamilyHandle, keyFrom, keyToExclude);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    /**
     * Используем newIteratorWithBase - для перебора вместе с ключами от родительской Таблицы
     * @return
     */
    @Override
    public RocksIterator getIterator() {
        if (quitIfNotAlive()) {
            return null;
        }

        // TODO double ITERATOR
        if (false) {
            return null; // почемуто итератор с Пакетом создается с двойным наборотм ключей, первая половина которых
            // не состоит в основном наборе
            // TEST - org.erachain.core.item.assets.OrderTest.iteratorDBMain
        } else {
            return writeBatch.newIteratorWithBase(dbCore.newIterator());
        }
    }

    /**
     * Используем newIteratorWithBase - для перебора вместе с ключами от родительской Таблицы
     * @return
     */
    @Override
    public RocksIterator getIterator(ColumnFamilyHandle indexDB) {
        if (quitIfNotAlive()) {
            return null;
        }

        return writeBatch.newIteratorWithBase(indexDB, dbCore.newIterator(indexDB));
    }

    @Override
    public void commit() {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();
        try {
            ////dbCore.flushWal(true);
            dbCore.write(writeOptions, writeBatch);
        } catch (RocksDBException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (false) {
                writeBatch.close();
                writeBatch = new WriteBatchWithIndex(true);
            } else {
                writeBatch.clear();
            }
            //logger.debug(" writeBatch commit");

            resetDbLock.readLock().unlock();
        }

    }

    @Override
    public void rollback() {
        if (quitIfNotAlive()) {
            return;
        }
        resetDbLock.readLock().lock();

        if (false) {
            writeBatch.close();
            writeBatch = new WriteBatchWithIndex(true);
        } else {
            writeBatch.clear();
        }

        //logger.debug("writeBatch rollback");

        resetDbLock.readLock().unlock();

    }

    @Override
    public void close() {
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) {
                return;
            }
            alive = false;
            writeBatch.close();
            ////dbCore.write(new WriteOptions().setSync(true), new WriteBatch());
            dbCore.syncWal();
            dbCore.closeE();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            //logger.debug("writeBatch close");
            resetDbLock.writeLock().unlock();
        }
    }

}
