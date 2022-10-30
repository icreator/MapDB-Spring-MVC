package com.boots.repository.dbs.rocksDB.transformation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ByteableTransaction implements Byteable<Transaction> {

    @Override
    public Transaction receiveObjectFromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return TransactionFactory.getInstance().parse(bytes, Transaction.FOR_DB_RECORD);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new WrongParseException(e);
        }
    }


    @Override
    public byte[] toBytesObject(Transaction value) {
        if (value == null)
            return null; // need for Filter KEYS = null

        return value.toBytes(Transaction.FOR_DB_RECORD, true);
    }
}
