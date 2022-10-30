package com.boots.repository.dbs.nativeMemMap;

import com.boots.repository.dbs.DBASet;
import com.boots.repository.dbs.DBTab;
import com.boots.repository.dbs.ForkedMap;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class NativeMapHashMapFork<T, U> extends DBMapSuitFork<T, U> implements ForkedMap {

    public NativeMapHashMapFork(DBTab parent, DBASet databaseSet, DBTab cover) {
        super(parent, databaseSet, null, cover);
    }

    @Override
    public void openMap() {

        // OPEN MAP
        map = new HashMap<T, U>();
        ///map = new HashMap<T, U>(Hasher.BYTE_ARRAY); - MapDB

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void createIndexes() {
    }

}
