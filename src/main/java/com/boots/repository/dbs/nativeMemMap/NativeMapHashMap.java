package com.boots.repository.dbs.nativeMemMap;

import com.boots.repository.dbs.DBASet;
import com.boots.repository.dbs.DBTab;
import com.boots.repository.dbs.mapDB.DBMapSuit;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;

import java.util.HashMap;

@Slf4j
public class NativeMapHashMap<T, U> extends DBMapSuit<T, U> {

    public NativeMapHashMap(DBASet databaseSet, DB database, DBTab cover) {
        super(databaseSet, database, false, cover);
    }

    @Override
    public void openMap() {

        // OPEN MAP
        map = new HashMap<T, U>();

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void createIndexes() {
    }

    @Override
    public void close() {
        cover = null;
        super.close();
    }

}
