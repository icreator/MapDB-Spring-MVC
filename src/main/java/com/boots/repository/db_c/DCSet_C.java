package com.boots.repository.db_c;

import com.boots.repository.dbs.DBASet;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.nio.file.Files;

@Slf4j
public class DCSet_C extends DBASet {

    /**
     * New version will auto-rebase DLSet from empty db file
     */
    final static int CURRENT_VERSION = 2;

    final private FPoolMap fPoolMap;

    public DCSet_C(File dbFile, DB database, boolean withObserver, boolean dynamicGUI) {
        super(dbFile, database, withObserver, dynamicGUI);
        this.fPoolMap = new FPoolMap(this, this.database);
    }
    
    static DB makeDB(File dbFile) {

        boolean isNew = !dbFile.exists();
        if (isNew) {
            dbFile.getParentFile().mkdirs();
        }

        DB database = DBMaker.newFileDB(dbFile)

                //// иначе кеширует блок и если в нем удалить трнзакции или еще что то выдаст тут же такой блок с пустыми полями
                ///// добавил dcSet.clearCache(); --
                ///.cacheDisable()

                // это чистит сама память если осталось 25% от кучи - так что она безопасная
                //.cacheHardRefEnable()
                //.cacheLRUEnable()
                ///.cacheSoftRefEnable()
                //.cacheWeakRefEnable()

                // количество точек в таблице которые хранятся в HashMap как в КЭШе
                .cacheSize(1 << 4)

                .checksumEnable()
                .mmapFileEnableIfSupported() // ++
                /// ICREATOR
                .commitFileSyncDisable() // ++

                // если при записи на диск блока процессор сильно нагружается - то уменьшить это
                .freeSpaceReclaimQ(7) // не нагружать процессор для поиска свободного места в базе данных

                //.compressionEnable()

                .transactionDisable()
                .make();

        if (isNew)
            DBASet.setVersion(database, CURRENT_VERSION);

        return database;

    }

    public static DCSet_C reCreateDB() {

        //OPEN DB
        File dbFile = new File(Settings.getInstance().getFPoolDir(), "data.dat");

        DB database = null;
        try {
            database = makeDB(dbFile);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            try {
                Files.walkFileTree(dbFile.getParentFile().toPath(),
                        new SimpleFileVisitorForRecursiveFolderDeletion());
            } catch (Throwable e1) {
                log.error(e1.getMessage(), e1);
            }
            database = makeDB(dbFile);
        }

        if (DBASet.getVersion(database) < CURRENT_VERSION) {
            database.close();
            log.warn("New Version: " + CURRENT_VERSION + ". Try remake DLSet.");
            try {
                Files.walkFileTree(dbFile.getParentFile().toPath(),
                        new SimpleFileVisitorForRecursiveFolderDeletion());
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }
            database = makeDB(dbFile);

        }

        return new DCSet_C(dbFile, database, true, true);

    }

    public FPoolMap getFPoolMap() {
        return this.fPoolMap;
    }
    
    @Override
    public void close() {

        if (this.database == null || this.database.isClosed())
            return;

        this.uses++;
        this.database.close();
        // улучшает работу финалайзера
        this.tables = null;
        this.uses--;

    }

}
