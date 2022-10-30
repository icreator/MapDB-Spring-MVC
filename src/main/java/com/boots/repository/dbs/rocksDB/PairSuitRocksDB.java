package com.boots.repository.dbs.rocksDB;

import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;

import java.util.ArrayList;

/**
 * Хранит сделки на бирже
 * Ключ: ссылка на иницатора + ссылка на цель
 * Значение - Сделка
 * Initiator DBRef (Long) + Target DBRef (Long) -> Trade
 */

@Slf4j
public class PairSuitRocksDB extends DBMapSuit<Tuple2<Long, Long>, TradePair> implements PairSuit {

    private final String NAME_TABLE = "PAIRS_TABLE";

    public PairSuitRocksDB(DBASet databaseSet, DB database) {
        super(databaseSet, database, logger, false);
    }

    @Override
    public void openMap() {

        map = new DBRocksDBTableDBCommitedAsBath<>(new ByteableTuple2LongLong(), new ByteableTrade(),
                NAME_TABLE, indexes,
                RocksDbSettings.initCustomSettings(7, 64, 32,
                        256, 10,
                        1, 256, 32, false),
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(),
                databaseSet, sizeEnable);
    }

    @Override
    protected void createIndexes() {
        // SIZE need count - make not empty LIST
        indexes = new ArrayList<>();

    }

    static void makeKey(byte[] buffer, long have, long want) {

        if (have > want) {
            System.arraycopy(Ints.toByteArray((int) have), 0, buffer, 0, 8);
            System.arraycopy(Ints.toByteArray((int) want), 0, buffer, 8, 8);
        } else {
            System.arraycopy(Ints.toByteArray((int) want), 0, buffer, 0, 8);
            System.arraycopy(Ints.toByteArray((int) have), 0, buffer, 8, 8);
        }

    }

    @Override
    public IteratorCloseable<Tuple2<Long, Long>> getIterator(long have) {
        return null;
    }

}
