package com.boots.repository.db_b;

import com.boots.repository.dbs.DBASet;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;

@Slf4j
public class DCSet_B  extends DBASet {

    /**
     * New version will auto-rebase DCSet from empty db file
     */
    final static int CURRENT_VERSION = 534; // vers 5.6.1 orderID
    
    private static final String LAST_BLOCK = "lastBlock";

    public final DCSet dcSet;

    private Atomic.Var<Long> licenseKeyVar;
    private Long licenseKey;

    private AllTelegramsMap telegramsMap;

    public DCSet_B(DCSet dcSet, File dbFile, DB database, boolean withObserver, boolean dynamicGUI) {
        super(dbFile, database, withObserver, dynamicGUI);

        this.dcSet = dcSet;

        // LICENCE SIGNED
        licenseKeyVar = database.getAtomicVar("licenseKey");
        licenseKey = licenseKeyVar.get();

        this.telegramsMap = new AllTelegramsMap(this, this.database);

    }

    /**
     * Создание файла для основной базы данных
     *
     * @param dbFile
     * @return
     */
    public static DB makeFileDB(File dbFile) {

        boolean isNew = !dbFile.exists();
        if (isNew) {
            dbFile.getParentFile().mkdirs();
        }

        //DELETE TRANSACTIONS
        //File transactionFile = new File(Settings.getInstance().getWalletDir(), "wallet.dat.t");
        //transactionFile.delete();

        DB database = DBMaker.newFileDB(dbFile)
                // убрал .closeOnJvmShutdown() it closing not by my code and rise errors! closed before my closing
                //.cacheSize(2048)

                //// иначе кеширует блок и если в нем удалить транзакции или еще что то выдаст тут же такой блок с пустыми полями
                ///// добавил dcSet.clearCache(); --
                ///.cacheDisable()

                // это чистит сама память если соталось 25% от кучи - так что она безопасная
                // у другого типа КЭША происходит утечка памяти
                //.cacheHardRefEnable()
                //.cacheLRUEnable()
                ///.cacheSoftRefEnable()
                .cacheWeakRefEnable() // analog new WeakReference() - в случае нехватки ппамяти кеш сам чистится

                // количество точек в таблице которые хранятся в HashMap как в КЭШе
                .cacheSize(1 << 14)

                .checksumEnable()
                .mmapFileEnableIfSupported() // ++

                // вызывает java.io.IOError: java.io.IOException: Запрошенную операцию нельзя выполнить для файла с открытой пользователем сопоставленной секцией
                // на ситема с Виндой в момент синхронизации кошелька когда там многот транзакций для этого кошелька
                .commitFileSyncDisable() // ++

                //.asyncWriteFlushDelay(30000)

                // если при записи на диск блока процессор сильно нагружается - то уменьшить это
                .freeSpaceReclaimQ(10) // не нагружать процессор для поиска свободного места в базе данных

                .mmapFileEnablePartial()
                //.compressionEnable()

                .make();

        if (isNew)
            DBASet.setVersion(database, CURRENT_VERSION);

        return database;

    }

    public synchronized static DCSet_B reCreateDB(DCSet dcSet, boolean withObserver, boolean dynamicGUI) {

        //OPEN DB
        File dbFile = new File(Settings.getInstance().getDataWalletPath(), "wallet.dat");

        DB database = null;
        try {
            database = makeFileDB(dbFile);
        } catch (Throwable e) {
            LOGGER.error(e.getMessage(), e);
            try {
                Files.walkFileTree(dbFile.getParentFile().toPath(),
                        new SimpleFileVisitorForRecursiveFolderDeletion());
            } catch (Throwable e1) {
                log.error(e1.getMessage(), e1);
            }
            database = makeFileDB(dbFile);
        }

        if (DBASet.getVersion(database) < CURRENT_VERSION) {
            database.close();
            log.warn("New Version: " + CURRENT_VERSION + ". Try remake DCSet_B in " + dbFile.getParentFile().toPath());
            try {
                Files.walkFileTree(dbFile.getParentFile().toPath(),
                        new SimpleFileVisitorForRecursiveFolderDeletion());
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }
            database = makeFileDB(dbFile);

        }

        return new DCSet_B(dcSet, dbFile, database, withObserver, dynamicGUI);

    }

    public Long getLicenseKey() {
        return this.licenseKey;
    }

    public void setLicenseKey(Long key) {

        this.licenseKey = key;
        this.licenseKeyVar.set(this.licenseKey);

    }

    public byte[] getLastBlockSignature() {
        this.uses++;
        Atomic.Var<byte[]> atomic = this.database.getAtomicVar(LAST_BLOCK);
        byte[] u = atomic.get();
        this.uses--;
        return u;
    }

    public void setLastBlockSignature(byte[] signature) {
        this.uses++;
        Atomic.Var<byte[]> atomic = this.database.getAtomicVar(LAST_BLOCK);
        atomic.set(signature);
        this.uses--;
    }


    public TelegramsMap getTelegramsMap() {
        return this.telegramsMap;
    }

    long commitPoint;

    public synchronized void hardFlush() {
        this.uses++;
        this.database.commit();
        this.uses--;

        commitPoint = System.currentTimeMillis();
    }

    @Override
    public void commit() {
        if (this.uses != 0
                || System.currentTimeMillis() - commitPoint < 10000
        )
            return;

        hardFlush();
    }

    public void clear(boolean andAccountsMap) {
        for (DBTab table : tables) {
            if (!andAccountsMap && table instanceof AccountMap)
                continue;

            table.clear();
        }
    }


    /**
     * закрываем без коммита! - чтобы при запуске продолжить?
     */
    @Override
    public void close() {

        if (this.database == null || this.database.isClosed())
            return;

        Controller.getInstance().getWallet().synchronizeBodyUsed = false;

        int step = 0;
        while (uses > 0 && ++step < 100) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }

        }

        this.uses++;
        try {

            this.database.commit();
            this.database.close();
            this.tables = null;
            this.database = null;
        } finally {
            this.uses--;
        }

    }

}
