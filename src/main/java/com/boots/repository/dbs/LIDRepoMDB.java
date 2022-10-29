package com.boots.repository.dbs;

import com.boots.entity.User;
import org.mapdb.Atomic;
import org.mapdb.BTreeMap;
import org.springframework.stereotype.Component;

import java.util.NavigableSet;

@Component
public class LIDRepoMDB<Long, V> extends MapDBRepository {

    MapDBSet mapDB;

    private static int CUT_NAME_INDEX = 12;

    protected Atomic.Long atomicKey;
    protected long key;

    private NavigableSet nameKey;

    HI = Long.MAX_VALUE;
    LO = 0L;

    public LIDRepoMDB(MapDBSet mapDB) {
        super(mapDB, mapDB.getUsers());

        atomicKey = mapDB.db.atomicLong(this.getClass().getName() + "_key").createOrOpen();
        key = atomicKey.get();

    }

}
