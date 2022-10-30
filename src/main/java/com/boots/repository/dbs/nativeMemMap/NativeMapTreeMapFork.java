package com.boots.repository.dbs.nativeMemMap;

import com.boots.repository.dbs.DBASet;
import com.boots.repository.dbs.DBTab;
import com.boots.repository.dbs.ForkedMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.TreeMap;

@Slf4j
public class NativeMapTreeMapFork<T, U> extends DBMapSuitFork<T, U> implements ForkedMap {

    public NativeMapTreeMapFork(DBTab parent, DBASet databaseSet, Comparator comparator, DBTab cover) {
        super(parent, databaseSet, comparator, cover);
    }

    @Override
    public void openMap() {

        // OPEN MAP
        if (COMPARATOR == null) {
            map = new TreeMap<T, U>();
        } else {
            map = new TreeMap<T, U>(COMPARATOR);
        }

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void createIndexes() {
    }

}
