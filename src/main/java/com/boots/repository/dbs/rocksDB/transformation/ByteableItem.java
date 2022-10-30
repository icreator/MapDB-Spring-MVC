package com.boots.repository.dbs.rocksDB.transformation;

import lombok.extern.slf4j.Slf4j;
import org.erachain.core.item.ItemCls;
import org.erachain.core.item.ItemFactory;
import org.erachain.core.transaction.Transaction;
import org.erachain.dbs.rocksDB.exceptions.WrongParseException;

@Slf4j
public class ByteableItem implements Byteable<ItemCls> {
    private int type;

    public ByteableItem(int type) {
        this.type = type;
    }

    @Override
    public ItemCls receiveObjectFromBytes(byte[] bytes) {
        try {
            return ItemFactory.getInstance().parse(Transaction.FOR_DB_RECORD, type, bytes, true);
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            throw new WrongParseException(e);
        }
    }

    @Override
    public byte[] toBytesObject(ItemCls value) {
        if (value == null)
            return null; // need for Filter KEYS = null

        return value.toBytes(Transaction.FOR_DB_RECORD, true, false);
    }
}
