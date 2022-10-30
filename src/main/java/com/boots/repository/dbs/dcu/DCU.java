package com.boots.repository.dbs.dcu;

import com.boots.repository.dbs.DBASet;
import com.boots.repository.dbs.DCUMapImpl;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.Serializer;

@Slf4j
public class DCU {
    /**
     * Тут нет использования другой базы данных - только MapDB для всех таблиц - поэтому немного другая логика - более простая
     *
     * суперкласс для таблиц цепочки блоков с функционалом Форканья (см. fork()
     * - универсальный в котром есть Мапка для Форка через getForkedMap
     * @param <T>
     * @param <U>
    <br><br>
    ВНИМАНИЕ !!! Вторичные ключи не хранят дубли - тоесть запись во втричном ключе не будет учтена иперезапишется если такой же ключ прийдет
    Поэтому нужно добавлять униальность

     */
    public abstract static class DCUMap<T, U> extends DCUMapImpl<T, U> {

        public DCUMap(DBASet databaseSet, DB database, String tabName, Serializer tabSerializer) {
            super(databaseSet, database, tabName, tabSerializer, false);
        }

        public DCUMap(DBASet databaseSet, DB database) {
            super(databaseSet, database, false);
        }
        public DCUMap(DBASet databaseSet, DB database, boolean sizeEnable) {
            super(databaseSet, database, sizeEnable);
        }

        public DCUMap(DCUMapImpl<T, U> parent, DBASet dcSet) {
            super(parent, dcSet, false);
        }

        public DCUMap(DCUMapImpl<T, U> parent, DBASet dcSet, boolean sizeEnable) {
            super(parent, dcSet, sizeEnable);
        }

    }
}
