package com.boots.repository.dbs.rocksDB;

import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;

@Slf4j
public class TransactionSuitRocksDBFork extends DBMapSuitFork<Long, Transaction> implements TransactionSuit
{

    ///private final String NAME_TABLE = "TRANSACTIONS_UNCONFIRMED_TABLE_FORK";

    public TransactionSuitRocksDBFork(TransactionMap parent, DBRocksDBTable parentMap, DBASet databaseSet) {
        super(parent, databaseSet, logger, true, null);
    }

    @Override
    public void openMap() {

        map = new DBRocksDBTableDB<>(new ByteableLong(), new ByteableTransactionUnc(), indexes,
                RocksDbSettings.initCustomSettings(7, 64, 32,
                        256, 10,
                        1, 256, 32, false),
                new WriteOptions().setSync(true).setDisableWAL(false), sizeEnable);
    }

    @Override
    public IteratorCloseable<Long> getTimestampIterator(boolean descending) {
        return null;
    }

    @Override
    public IteratorCloseable<Long> typeIterator(String sender, Long timestamp, Integer type) {
        return null;
    }

    @Override
    public IteratorCloseable<Long> senderIterator(String sender) {
        return null;
    }

    @Override
    public IteratorCloseable<Long> recipientIterator(String recipient) {
        return null;
    }

    @Override
    public boolean writeToParent() {

        boolean updated = false;

        // сперва нужно удалить старые значения
        // см issues/1276

        if (deleted != null) {
            Iterator<Long> iteratorDeleted = this.deleted.keySet().iterator();
            while (iteratorDeleted.hasNext()) {
                // тут через Очередь сработает - без ошибок от закрытия
                parent.delete(iteratorDeleted.next());
                updated = true;
            }
            deleted = null;
        }

        // теперь внести новые

        Iterator<Long> iterator = this.map.keySet().iterator();

        while (iterator.hasNext()) {
            Long key = iterator.next();
            // тут через Очередь сработает - без ошибок от закрытия
            parent.put(key, this.map.get(key));
            updated = true;
        }

        return updated;
    }

}
