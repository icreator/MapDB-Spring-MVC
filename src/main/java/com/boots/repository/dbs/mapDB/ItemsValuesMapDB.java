package com.boots.repository.dbs.mapDB;

import com.boots.repository.dbs.DBASet;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.parboiled.common.Tuple3;


@Slf4j
public class ItemsValuesMapDB extends DBMapSuit<Tuple3<Long, Byte, byte[]>, byte[]> {

    public ItemsValuesMapDB(DBASet databaseSet, DB database) {
        super(databaseSet, database, logger, false);
    }

    @Override
    public void openMap() {

        LO = new Tuple3(0L, (byte) 0, new byte[0]);
        HI = new Tuple3(Long.MAX_VALUE, Byte.MAX_VALUE, new byte[255]);

        //OPEN MAP
        map = database.treeMap("items_values").createOrOpen()
                .comparator(new Fun.Tuple3Comparator<>(Fun.COMPARATOR, Fun.COMPARATOR, Fun.BYTE_ARRAY_COMPARATOR))
                .makeOrGet();

    }

}
