package com.boots.repository.dbs.mapDB.map;

import com.boots.repository.dbs.mapDB.map.TransactionFinalMapSignsSuit;
import com.boots.repository.dbs.DBASet;
import com.boots.repository.dbs.mapDB.DBMapSuit;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;


/**
 * Хранит блоки полностью - с транзакциями
 * <p>
 * ключ: номер блока (высота, height)<br>
 * занчение: Блок<br>
 * <p>
 * Есть вторичный индекс, для отчетов (blockexplorer) - generatorMap
 * TODO - убрать длинный индек и вставить INT
 *
 * @return
 */

@Slf4j
public class TransactionFinalSignsSuitMapDB extends DBMapSuit<byte[], Long> implements TransactionFinalMapSignsSuit {


    public TransactionFinalSignsSuitMapDB(DBASet databaseSet, DB database, boolean sizeEnable) {
        super(databaseSet, database, sizeEnable);
    }

    @Override
    public void openMap() {
        //OPEN MAP
        // HASH map is so QUICK
        DB.HashMapMaker mapConstruct = database.hashMap("signature_final_tx")
                .keySerializer(SerializerBase.BYTE_ARRAY)
                .hasher(Hasher.BYTE_ARRAY)
                .valueSerializer(SerializerBase.LONG);

        if (sizeEnable)
            mapConstruct = mapConstruct.counterEnable();

        map = mapConstruct.createOrOpen();
    }

}
