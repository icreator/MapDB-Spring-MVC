package com.boots.repository.db_c;

import com.boots.repository.dbs.DCUMapImpl;
import lombok.extern.slf4j.Slf4j;
import org.erachain.dbs.DCUMapImpl;
import org.mapdb.DB;

import java.util.TreeMap;

/**
 */
@Slf4j
public class FPoolMap extends DCUMapImpl<String, Object[]> {

    public FPoolMap(DCSet_C databaseSet, DB database) {
        super(databaseSet, database);
    }

    @Override
    public void openMap() {
        //OPEN MAP
        map = database.createTreeMap("pool")
                .makeOrGet();
    }

    @Override
    protected void getMemoryMap() {
        map = new TreeMap<String, Object[]>();
    }

}
