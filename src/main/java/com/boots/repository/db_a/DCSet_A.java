package com.boots.repository.db_a;

import com.boots.repository.CONST;
import com.boots.repository.dbs.DBASet;
import com.boots.repository.IDB;
import com.boots.repository.dbs.DBTab;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.swing.*;
import java.io.Closeable;
import java.io.File;
import java.io.IOError;
import java.nio.file.Files;
import java.util.Random;

import static com.boots.repository.IDB.*;

/**
 * набор таблиц. Поидее тут нужно хранить список таблиц и ссылку на родителя при Форке базы.
 * Но почемуто парент хранится в каждой таблице - хотя там сразу ссылка на форкнутую таблицу есть
 * а в ней уже хранится объект набора DCSet_A
 */
@Slf4j

public class DCSet_A extends DBASet implements Closeable  {


    /**
     * New version will auto-rebase DCSet_A from empty db file
     */
    final static int CURRENT_VERSION = 542;

    /**
     * Используется для отладки - где незакрытый набор таблиц остался.
     * Делаем дамн КУЧИ в VisualVM и там в параметрах смотрим откуда этот объект был создан
     */
    public String makedIn = "--";

    public static final String DATA_FILE = "chain.dat";

    private static final int ACTIONS_BEFORE_COMMIT = (1<<20)
            << (CONST.getInstance().databaseSystem == DBS_MAP_DB ? 1 : 3);
    // если все на Рокс перевели то меньше надо ставить
    public static final long MAX_ENGINE_BEFORE_COMMIT = ACTIONS_BEFORE_COMMIT;
    private static final long TIME_COMPACT_DB = 1L * 24L * 3600000L;
    public static final long DELETIONS_BEFORE_COMPACT = (long) ACTIONS_BEFORE_COMMIT;

    /**
     * Включает подсчет количество в основной таблице транзакций или в Таблице с подписями
     */
    static private boolean SIZE_ENABLE_IN_FINAL = true;

    // эти настройки по умолчанию при ФАСТ режиме пойдут

    /**
     * DBS_MAP_DB - fast, DBS_ROCK_DB - slow
     */
    public static final int BLOCKS_MAP = DBS_ROCK_DB;
    public static final int BLOCKS_MAP_FORK = DBS_NATIVE_MAP;
    /**
     * DBS_MAP_DB - slow then DBS_ROCK_DB
     */
    public static final int FINAL_TX_MAP = DBS_ROCK_DB;
    public static final int FINAL_TX_MAP_FORK = DBS_NATIVE_MAP;

    /**
     * DBS_MAP_DB - fast, DBS_ROCK_DB - slow
     */
    public static final int FINAL_TX_SIGNS_MAP = DBS_MAP_DB;
    public static final int FINAL_TX_SIGNS_MAP_FORK = DBS_MAP_DB;

    /**
     * DBS_MAP_DB - slow, DBS_ROCK_DB - crash, DBS_MAP_DB_IN_MEM - fast
     * нельзя делать DBS_NATIVE_MAP !!! - так как он не удаляет транзакции по вторичному индексу
     * И трнзакции копятся пока полностью не будут удалены скопом при FLUSH что тормозит время
     * блока на проверке и исполнении
     */
    public static final int UNCONF_TX_MAP = DBS_MAP_DB_IN_MEM;;
    public static final int UNCONF_TX_MAP_FORK = DBS_MAP_DB_IN_MEM;

    /**
     * DBS_MAP_DB - good, DBS_ROCK_DB - very SLOW потому что BigDecimal 20 байт - хотя с -opi это не делаем
     */
    public static final int ACCOUNT_BALANCES = DBS_MAP_DB;
    public static final int ACCOUNT_BALANCES_FORK = DBS_NATIVE_MAP;

    /**
     * DBS_MAP_DB - fast, DBS_ROCK_DB - slow
     */
    public static final int ACCOUNTS_REFERENCES = DBS_MAP_DB;

    public static final int ORDERS_MAP = DBS_MAP_DB;
    public static final int COMPLETED_ORDERS_MAP = DBS_ROCK_DB;
    public static final int TIME_DONE_MAP = DBS_ROCK_DB;
    public static final int TIME_WAIT_MAP = DBS_ROCK_DB;
    public static final int TRADES_MAP = DBS_MAP_DB;
    public static final int PAIRS_MAP = DBS_MAP_DB;

    public static final int ITEMS_VALUES_MAP = DBS_MAP_DB;

    /**
     * если задано то выбран такой КЭШ который нужно самим чистить иначе реперолнение будет
     */
    private static boolean needClearCache = false;

    private static boolean isStoped = false;
    private volatile static DCSet_A instance;
    private DCSet_A parent;

    private boolean inMemory = false;

    private BlockChain bchain;

    private ItemPersonMap itemPersonMap;

    private TransactionFinalMapImpl transactionFinalMap;
    private TransactionFinalCalculatedMap transactionFinalCalculatedMap;
    private TransactionFinalMapSigns transactionFinalMapSigns;
    private TransactionMapImpl transactionTab;

    private TimeTXDoneMap timeTXDoneMap;
    private TimeTXWaitMap timeTXWaitMap;


    private long actions = (long) (Math.random() * (ACTIONS_BEFORE_COMMIT >> 1));

    /**
     *
     * @param dbFile
     * @param database общая база данных для данного набора - вообще надо ее в набор свтавить и все.
     *               У каждой таблицы внутри может своя база данных открытьваться.
     *               А команды базы данных типа close commit должны из таблицы передаваться в свою.
     *               Если в общей базе таблица, то не нужно обработка так как она делается в наборе наверху
     * @param withObserver
     * @param dynamicGUI
     * @param inMemory
     * @param defaultDBS
     */
    public DCSet_A(File dbFile, DB database, boolean withObserver, boolean dynamicGUI, boolean inMemory, int defaultDBS) {
        super(dbFile, database, withObserver, dynamicGUI);

        log.info("UP SIZE BEFORE COMMIT [KB]: " + MAX_ENGINE_BEFORE_COMMIT
                + ", ACTIONS BEFORE COMMIT: " + ACTIONS_BEFORE_COMMIT
                + ", DELETIONS BEFORE COMPACT: " + DELETIONS_BEFORE_COMPACT);

        this.inMemory = inMemory;

        try {
            // переделанные таблицы

            this.timeTXDoneMap = new TimeTXDoneMap(defaultDBS != DBS_FAST ? defaultDBS :
                    TIME_DONE_MAP
                    , this, database);
            this.timeTXWaitMap = new TimeTXWaitMap(defaultDBS != DBS_FAST ? defaultDBS :
                    TIME_WAIT_MAP
                    , this, database);

            this.actions = 0L;

            this.transactionFinalCalculatedMap = new TransactionFinalCalculatedMap(this, database);


            // IT OPEN AFTER ALL OTHER for make secondary keys and setDCSet_A
            this.transactionFinalMap = new TransactionFinalMapImpl(defaultDBS != DBS_FAST ? defaultDBS :
                    FINAL_TX_MAP
                    , this, database, SIZE_ENABLE_IN_FINAL);

            this.transactionFinalMapSigns = new TransactionFinalMapSignsImpl(defaultDBS != DBS_FAST ? defaultDBS :
                    FINAL_TX_SIGNS_MAP
                    , this, database, !SIZE_ENABLE_IN_FINAL);

            this.transactionTab = new TransactionMapImpl(UNCONF_TX_MAP, this, database);

        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            this.close();
            throw e;
        }

        if (false // теперь отклучаем счетчики для усклрения работы - отсвили только в Подписи
                &&this.blockMap.size() != this.blocksHeadsMap.size()
                || this.blockSignsMap.size() != this.blocksHeadsMap.size()) {
            log.info("reset DATACHAIN on height error (blockMap, blockSignsMap, blocksHeadsMap: "
                    + this.blockMap.size() + " != "
                    + this.blockSignsMap.size() + " != " + this.blocksHeadsMap.size());

            this.close();
            this.actions = -1;

        }
        uses--;

    }

    public DCSet_A(File dbFile, boolean withObserver, boolean dynamicGUI, boolean inMemory, int defaultDBS) {
        this(dbFile, DCSet_A.makeFileDB(dbFile), withObserver, dynamicGUI, inMemory, defaultDBS);
    }

    /**
     * Make data set as Fork
     *
     * @param parent     parent DCSet_A
     * @param idDatabase
     */
    protected DCSet_A(DCSet_A parent, DB idDatabase) {

        if (Runtime.getRuntime().maxMemory() == Runtime.getRuntime().totalMemory()) {
            if (Runtime.getRuntime().freeMemory() < (Runtime.getRuntime().totalMemory() >> 10)
                    + (CONST.MIN_MEMORY_TAIL)) {

                //log.debug("########################### Max=Total Memory [MB]:" + (Runtime.getRuntime().totalMemory() >> 20));
                //log.debug("########################### Free Memory [MB]:" + (Runtime.getRuntime().freeMemory() >> 20));

                // у родителя чистим - у себя нет, так как только создали
                parent.clearCache();
                System.gc();
                if (Runtime.getRuntime().freeMemory() < (Runtime.getRuntime().totalMemory() >> 10)
                        + (CONST.MIN_MEMORY_TAIL << 1)) {
                    log.error("Heap Memory Overflow");
                    CONST.getInstance().stopAndExit(1091);
                    return;
                }
            }
        }

        this.addUses();

        this.database = idDatabase;
        this.parent = parent;
        ///this.database = parent.database.snapshot();
        this.bchain = parent.bchain;

        // переделанные поновой таблицы
        this.transactionTab = new TransactionMapImpl(
                UNCONF_TX_MAP_FORK
                , parent.transactionTab, this);
        this.transactionFinalMap = new TransactionFinalMapImpl(
                FINAL_TX_MAP_FORK
                , parent.transactionFinalMap, this);

        this.transactionFinalMapSigns = new TransactionFinalMapSignsImpl(
                FINAL_TX_SIGNS_MAP_FORK
                , parent.transactionFinalMapSigns, this, true);

        this.timeTXDoneMap = new TimeTXDoneMap(
                DBS_MAP_DB
                //DBS_ROCK_DB
                //DBS_NATIVE_MAP
                , parent.timeTXDoneMap, this);
        this.timeTXWaitMap = new TimeTXWaitMap(
                DBS_MAP_DB
                //DBS_ROCK_DB
                //DBS_NATIVE_MAP
                , parent.timeTXWaitMap, this);

        this.transactionFinalCalculatedMap = new TransactionFinalCalculatedMap(parent.transactionFinalCalculatedMap, this);
        this.itemPersonMap = new ItemPersonMap(parent.getItemPersonMap(), this);

        this.outUses();
    }

    /**
     * Get instance of DCSet_A or create new
     *
     * @param withObserver [true] - for switch on GUI observers
     * @param dynamicGUI   [true] - for switch on GUI observers fir dynamic interface
     * @return
     * @throws Exception
     */

    public static DCSet_A getInstance(boolean withObserver, boolean dynamicGUI, boolean inMemory) throws Exception {
        if (instance == null) {
            if (inMemory) {
                reCreateDBinMEmory(withObserver, dynamicGUI);
            } else {
                reCreateDB(withObserver, dynamicGUI);
            }
        }

        return instance;
    }

    /**
     * @return
     */
    public static DCSet_A getInstance() {
        return instance;
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

        /// https://jankotek.gitbooks.io/mapdb/performance/
        DBMaker databaseStruc = DBMaker.newFileDB(dbFile)
                // убрал .closeOnJvmShutdown() it closing not by my code and rise errors! closed before my closing

                .checksumEnable()
                .mmapFileEnableIfSupported() // ++ but -- error on asyncWriteEnable

                // тормозит сильно но возможно когда файл большеой не падает скорость сильно
                // вдобавок не сохраняет на диск даже Транзакционный файл и КРАХ теряет данные
                // НЕ ВКЛЮЧАТЬ!
                // .mmapFileEnablePartial()

                // Если этот отключить (закомментировать) то файлы на лету не обновляются на диске а только в момент Флуша
                // типа быстрее работают но по факту с Флушем нет и в описании предупрежджение - что
                // при крахе системы в момент флуша можно потерять данные - так как в Транзакционный фал изменения
                // катаются так же в момент флуша - а не при изменении данных
                // так что этот ключ тут полезный
                .commitFileSyncDisable() // ++

                //.snapshotEnable()
                //.asyncWriteEnable() - крах при коммитах и откатах тразакций - возможно надо asyncWriteFlushDelay больше задавать
                .asyncWriteFlushDelay(2)

                // если при записи на диск блока процессор сильно нагружается - то уменьшить это
                .freeSpaceReclaimQ(BlockChain.TEST_DB > 0 ? 3 : 7)// не нагружать процессор для поиска свободного места в базе данных

                //.compressionEnable()
                ;

        /**
         * если не задавать вид КЭШа то берется стандартный - и его размер 10 очень мал и скорость
         * решения боков в 2-5 раза меньше. Однако если разделить таблицы по разным базам так чтобы блоки особо не кэшировать.
         * Тогда возможно этот вид КЭШа будет приемлем для дранзакций
         * == количество точек в таблице которые хранятся в HashMap как в КЭШе
         * - начальное значени для всех UNBOUND и максимальное для КЭШ по умолчанию
         * WAL в кэш на старте закатывает все значения - ограничим для быстрого старта
         */

        if (Controller.CACHE_DC.equals("off")) {
            databaseStruc.cacheDisable();
            needClearCache = false;
        } else {
            // USE CACHE
            if (true || BLOCKS_MAP != DBS_MAP_DB) {
                // если блоки не сохраняются в общей базе данных, а трнзакции мелкие по размеру
                databaseStruc.cacheSize(32 + 32 << Controller.HARD_WORK);
            } else {
                // если блоки в этой MapDB то уменьшим - так как размер блока может быть большой
                databaseStruc.cacheSize(32 + 32 << Controller.HARD_WORK);
            }

            // !!! кэш по умолчанию на количество записей - таблица в памяти
            // !!! - может быстро съесть память ((
            // !!! если записи (блоки или единичные транзакции) большого объема!!!

            switch (Controller.CACHE_DC) {
                case "lru":
                    // при норм размере и достаточной памяти скорость не хуже чем у остальных
                    // скорость зависит от памяти и настроек -
                    databaseStruc.cacheLRUEnable();
                    needClearCache = true;
                    break;
                case "weak":
                    // analog new cacheSoftRefE - в случае нехватки памяти кеш сам чистится
                    databaseStruc.cacheWeakRefEnable();
                    needClearCache = false;
                    break;
                case "soft":
                    // analog new WeakReference() - в случае нехватки памяти кеш сам чистится
                    databaseStruc.cacheSoftRefEnable();
                    needClearCache = false;
                    break;
                default:
                    // это чистит сама память если осталось 25% от кучи - так что она безопасная
                    // самый быстрый
                    // но чистится каждые 10 тыс обращений - org.mapdb.Caches.HardRef
                    // - опасный так как может поесть память быстро!
                    databaseStruc.cacheHardRefEnable();
                    needClearCache = true;
                    break;
            }
        }

        DB database = databaseStruc.make();
        if (isNew)
            DBASet.setVersion(database, CURRENT_VERSION);

        return database;

    }

    /**
     * Для проверки одного блока в памяти - при добавлении в цепочку или в буфер ожидания
     */
    public static boolean needResetUTXPoolMap = false;

    public static DB makeDBinMemory() {

        // лучше для памяти ставить наилучшее сжатие чтобы память не кушать лишний раз
        int freeSpaceReclaimQ = 10;
        needResetUTXPoolMap = freeSpaceReclaimQ < 3;
        return DBMaker
                .newMemoryDB()
                .transactionDisable()
                .deleteFilesAfterClose()
                .asyncWriteEnable() // улучшает чуток и не падает так как нет транзакционно

                // это время добавляется к ожиданию конца - и если больше 100 то тормоз лишний
                // но 1..10 - увеличивает скорость валидации транзакций!
                .asyncWriteFlushDelay(2)
                // тут не влияет .commitFileSyncDisable()

                //.cacheHardRefEnable()
                //.cacheLRUEnable()
                .cacheWeakRefEnable() // new WeakReference()
                //.cacheDisable()


                // если задано мене чем 3 то очитска записей при их удалении вобще не происходит - поэтому база раздувается в памяти без огрничений
                // в этом случае нужно ее закрывать удалять и заново открывать
                .freeSpaceReclaimQ(freeSpaceReclaimQ) // как-то слабо влияет в памяти
                //.compressionEnable() // как-то не влияет в памяти

                //
                //.newMemoryDirectDB()
                .make();
    }

    /**
     * remake data set
     *
     * @param withObserver [true] - for switch on GUI observers
     * @param dynamicGUI   [true] - for switch on GUI observers fir dynamic interface
     * @throws Exception
     */
    public static void reCreateDB(boolean withObserver, boolean dynamicGUI) throws Exception {

        //OPEN DB
        File dbFile = new File(Settings.getInstance().getDataChainPath(), DATA_FILE);

        DB database = null;
        try {
            database = makeFileDB(dbFile);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
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
            log.warn("New Version: " + CURRENT_VERSION + ". Try remake DCSet_A in " + dbFile.getParentFile().toPath());

            if (Controller.getInstance().useGui) {
                Object[] options = {Lang.T("Rebuild locally"),
                        Lang.T("Clear chain"),
                        Lang.T("Exit")};

                //As the JOptionPane accepts an object as the message
                //it allows us to use any component we like - in this case
                //a JPanel containing the dialog components we want

                int n = JOptionPane.showOptionDialog(
                        null,
                        Lang.T("Updating the database structure %1").replace("%1", "" + CURRENT_VERSION)
                                + " \n" + Lang.T(""),
                        Lang.T("Updating the version"),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        2
                );

                if (n == JOptionPane.YES_OPTION) {
                    Controller.getInstance().reBuildChain = true;
                    Controller.getInstance().reBuilChain();
                }

                if (n == JOptionPane.YES_OPTION || n == JOptionPane.NO_OPTION) {
                    try {
                        Files.walkFileTree(dbFile.getParentFile().toPath(),
                                new SimpleFileVisitorForRecursiveFolderDeletion());
                    } catch (Throwable e) {
                        log.error(e.getMessage(), e);
                    }
                    database = makeFileDB(dbFile);
                } else {
                    Controller.getInstance().stopAndExit(-22);
                }

            } else {
                log.warn("Please rebuild chain local by use '-rechain' parameter (quick case) or delete folder " + dbFile.getParentFile().toPath() + " for full synchronize chain from network (slow case).");
                System.exit(-22);
            }

        }

        //CREATE INSTANCE
        instance = new DCSet_A(dbFile, database, withObserver, dynamicGUI, false, Controller.getInstance().databaseSystem);
        if (instance.actions < 0) {
            for (DBTab tab : instance.tables) {
                tab.clear();
            }
            database.close();
            dbFile.delete();
            throw new Exception("error in DATACHAIN:" + instance.actions);
        }

        // очистим полностью перед компактом
        if (Controller.getInstance().compactDConStart) {
            instance.getTransactionTab().clear();
            instance.database.commit();
            log.debug("try COMPACT");
            database.compact();
            log.debug("COMPACTED");
        }

    }

    public static void reCreateDBinMEmory(boolean withObserver, boolean dynamicGUI) {
        DB database = makeDBinMemory();

        instance = new DCSet_A(null, database, withObserver, dynamicGUI, true, Controller.getInstance().databaseSystem);

    }

    /**
     * make data set in memory. For tests
     *
     * @param defaultDBS
     * @return
     */
    public static DCSet_A createEmptyDatabaseSet(int defaultDBS) {
        DB database = DCSet_A.makeDBinMemory();

        instance = new DCSet_A(null, database, false, false, true, defaultDBS);
        return instance;
    }

    public static DCSet_A createEmptyHardDatabaseSet(File dbFile, boolean DCSet_AWithObserver, boolean dynamicGUI, int defaultDBS) {
        DB database = makeFileDB(dbFile);
        instance = new DCSet_A(dbFile, database, DCSet_AWithObserver, dynamicGUI, false, defaultDBS);
        return instance;
    }

    public static DCSet_A createEmptyHardDatabaseSet(int defaultDBS) {
        instance = new DCSet_A(null, getHardBaseForFork(), false, false, true, defaultDBS);
        return instance;
    }

    public static DCSet_A createEmptyHardDatabaseSetWithFlush(String path, int defaultDBS) {
        // найдем новый не созданный уже файл
        File dbFile;
        do {
            dbFile = new File(path == null? Settings.getInstance().getDataTempDir() : path, "fork" + randFork.nextInt() + ".dat");
        } while (dbFile.exists());

        dbFile.getParentFile().mkdirs();

        instance = new DCSet_A(dbFile, makeFileDB(dbFile), false, false, true, defaultDBS);
        return instance;
    }

    public static boolean isStoped() {
        return isStoped;
    }

    public boolean inMemory() {
        return this.inMemory || this.parent != null;
    }

    @Override
    public void addUses() {
        if (this.parent != null) {
            return;
        }
        this.uses++;
    }

    @Override
    public void outUses() {
        if (this.parent != null) {
            return;
        }
        this.uses--;
    }

    /**
     * reset all data set
     */
    public void reset() {

        this.addUses();

        this.transactionFinalMap.clear();
        this.transactionFinalCalculatedMap.clear();
        this.transactionFinalMapSigns.clear();
        this.transactionTab.clear();

        this.itemPersonMap.clear();

        this.timeTXDoneMap.clear();
        this.timeTXWaitMap.clear();

        this.outUses();
    }

    /**
     * Взять родительскую базу, с которой сделан форк. Используется в процессах транзакций
     * @return
     */
    public DCSet_A getParent() {
        return this.parent;
    }

    /**
     * это форкнутая база?
     * @return
     */
    public boolean isFork() {
        return this.parent != null;
    }

    /**************************************************************************************************/


    /**
     * Транзакции занесенные в цепочку
     *
     * block.id + tx.ID in this block -> transaction
     *
     * Вторичные ключи:
     * ++ sender_txs
     * ++ recipient_txs
     * ++ address_type_txs
     */
    public TransactionFinalMapImpl getTransactionFinalMap() {
        return this.transactionFinalMap;
    }


    /**
     * Поиск по подписи ссылки на трнзакыию
     * signature -> <BlockHeoght, Record No>
     */
    public TransactionFinalMapSigns getTransactionFinalMapSigns() {
        return this.transactionFinalMapSigns;
    }

    /**
     * Храним неподтвержденные транзакции - memory pool for unconfirmed transaction
     *
     * Также хранит инфо каким пирам мы уже разослали транзакцию неподтвержденную так что бы при подключении делать автоматически broadcast
     *
     * signature -> Transaction
     * TODO: укоротить ключ до 8 байт
     *
     * ++ seek by TIMESTAMP
     */
    public TransactionMapImpl getTransactionTab() {
        return this.transactionTab;
    }


    /************************************** ITEMS *************************************/

    /**
     * see datachain.ItemMap
     *
     * @return
     */
    public ItemPersonMap getItemPersonMap() {
        return this.itemPersonMap;
    }


    public TimeTXDoneMap getTimeTXDoneMap() {
        return this.timeTXDoneMap;
    }

    public TimeTXWaitMap getTimeTXWaitMap() {
        return this.timeTXWaitMap;
    }


    static Random randFork = new Random();

    /**
     * Эта база используется для откатов, возможно глубоких
     *
     * @param dbFile
     * @return
     */
    public static DB getHardBaseForFork(File dbFile) {

        dbFile.getParentFile().mkdirs();

        /// https://jankotek.gitbooks.io/mapdb/performance/
        //CREATE DATABASE
        DB database = DBMaker.newFileDB(dbFile)

                // включим самоудаление после закрытия
                .deleteFilesAfterClose()
                .transactionDisable()

                ////// ТУТ вряд ли нужно КЭШИРОВАТь при чтении что-либо
                //////
                // это чистит сама память если соталось 25% от кучи - так что она безопасная
                // у другого типа КЭША происходит утечка памяти
                //.cacheHardRefEnable()
                //.cacheLRUEnable()
                ///.cacheSoftRefEnable()
                .cacheWeakRefEnable()

                // количество точек в таблице которые хранятся в HashMap как в КЭШе
                // - начальное значени для всех UNBOUND и максимальное для КЭШ по умолчанию
                // WAL в кэш на старте закатывает все значения - ограничим для быстрого старта
                .cacheSize(2048)

                //.checksumEnable()
                .mmapFileEnableIfSupported() // ++ but -- error on asyncWriteEnable

                // тормозит сильно но возможно когда файл большеой не падает скорость сильно
                // вдобавок не сохраняет на диск даже Транзакционный файл и КРАХ теряет данные
                // НЕ ВКЛЮЧАТЬ!
                // .mmapFileEnablePartial()

                .commitFileSyncDisable() // ++

                //.snapshotEnable()
                .asyncWriteEnable() // тут нет Коммитов поэтому должно работать
                .asyncWriteFlushDelay(2)

                // если при записи на диск блока процессор сильно нагружается - то уменьшить это
                .freeSpaceReclaimQ(5) // не нагружать процессор для поиска свободного места в базе данных

                .make();

        return database;
    }

    public static DB getHardBaseForFork() {
        //OPEN DB

        // найдем новый не созданный уже файл
        File dbFile;
        do {
            dbFile = new File(Settings.getInstance().getDataTempDir(), "fork" + randFork.nextInt() + ".dat");
        } while (dbFile.exists());

        dbFile.getParentFile().mkdirs();

        return getHardBaseForFork(dbFile);
    }

    /**
     * создать форк
     *
     * @return
     */
    public DCSet_A fork(DB database, String maker) {
        this.addUses();

        try {
            DCSet_A fork = new DCSet_A(this, database);
            fork.makedIn = maker;

            this.outUses();
            return fork;

        } catch (java.lang.OutOfMemoryError e) {
            log.error(e.getMessage(), e);

            this.outUses();

            Controller.getInstance().stopAndExit(1113);
            return null;
        }

    }

    /**
     * USe inMemory MapDB Database
     *
     * @param maker
     * @return
     */
    public DCSet_A fork(String maker) {
        return fork(makeDBinMemory(), maker);
    }

    /**
     * Нужно незабыть переменные внктри каждой таблицы тоже в Родителя скинуть
     */
    @Override
    public synchronized void writeToParent() {

        // проверим сначала тут память чтобы посередине не вылететь
        if (Runtime.getRuntime().maxMemory() == Runtime.getRuntime().totalMemory()) {
            // System.out.println("########################### Free Memory:"
            // + Runtime.getRuntime().freeMemory());
            if (Runtime.getRuntime().freeMemory() < (Runtime.getRuntime().totalMemory() >> 10)
                    + (Controller.MIN_MEMORY_TAIL)) {
                // у родителя чистим - у себя нет, так как только создали
                parent.clearCache();
                System.gc();
                if (Runtime.getRuntime().freeMemory() < (Runtime.getRuntime().totalMemory() >> 10)
                        + (Controller.MIN_MEMORY_TAIL << 1)) {
                    log.error("Heap Memory Overflow before commit");
                    Controller.getInstance().stopAndExit(9618);
                    return;
                }
            }
        }

        try {
            // до сброса обновим - там по Разсеру таблицы - чтобы не влияло новой в Родителе и а Форке
            // иначе размер больше будет в форке и не то значение
            ((BlockMap) blockMap.getParent()).setLastBlockSignature(blockMap.getLastBlockSignature());

            for (DBTab table : tables) {
                table.writeToParent();
            }
            // теперь нужно все общие переменные переопределить
        } catch (Exception e) {

            log.error(e.getMessage(), e);

            // база битая - выходим!! Хотя rollback должен сработать
            Controller.getInstance().stopAndExit(9613);
            return;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);

            // база битая - выходим!! Хотя rollback должен сработать
            Controller.getInstance().stopAndExit(9615);
        }

    }

    @Override
    public synchronized void close() {

        if (this.database != null) {
            // THIS IS not FORK
            if (!this.database.isClosed()) {
                this.addUses();

                // если основная база и шла обработка блока, то с откатом
                if (parent == null) {
                    if (this.getBlockMap().isProcessing()) {
                        log.debug("TRY ROLLBACK");
                        for (DBTab tab : tables) {
                            try {
                                tab.rollback();
                            } catch (IOError e) {
                                log.error(e.getMessage(), e);
                            }
                        }

                        try {
                            this.database.rollback();
                        } catch (IOError e) {
                            log.error(e.getMessage(), e);
                        }

                        // not need on close!
                        // getBlockMap().resetLastBlockSignature();
                    } else {
                        for (DBTab tab : tables) {
                            try {
                                tab.commit();
                            } catch (IOError e) {
                                log.error(tab.toString() + ": " + e.getMessage(), e);
                            }
                        }

                        try {
                            this.database.commit();
                        } catch (IOError e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }

                for (DBTab tab : tables) {
                    try {
                        tab.close();
                    } catch (IOError e) {
                        log.error(e.getMessage(), e);
                    }
                }
                // улучшает работу финализера
                tables = null;
                try {
                    this.database.close();
                } catch (IOError e) {
                    log.error(e.getMessage(), e);
                }
                // улучшает работу финализера
                this.database = null;

                this.uses = 0;
            }

            log.info("closed " + (parent == null ? "Main" : "parent " + toString()));
        }

    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    @Override
    public void commit() {
        this.commitSize += 5000;
    }

    public void rollback() {
        this.addUses();
        for (DBTab tab : tables) {
            tab.rollback();
        }

        this.database.rollback();

        for (DBTab tab : tables) {
            tab.afterRollback();
        }

        this.actions = 0L;
        this.outUses();
    }

    public void clearCache() {
        for (DBTab tab : tables) {
            tab.clearCache();
        }
        super.clearCache();
    }

    private long pointFlush = System.currentTimeMillis();
    private long pointCompact = pointFlush;
    private long pointClear;
    private long commitSize;
    private boolean clearGC = false;

    /**
     * Освобождает память, которая вне кучи приложения но у системы эта память забирается
     * - ее само приложение и сборщик мусора не смогут освободить.
     * Причем размер занимаемой памяти примерно равен файлу chain.dat.t - в котором транзакция СУБД MapDB хранится.
     * При коммитре этот файл очищается. Размер файла получается больше чем размер блока,
     * так как данные дублиуются в таблице трнзакций и еще активы (сущности - для картинок и описний).
     * <p>
     * TODO нужно сделать по размеру этого файла - если большой - то коммит закрыть - так как не все данные могут в MApDB сохраняться - часть в RocksDB - а там другой файл и другая память
     *
     * @param size
     * @param hardFlush
     * @param doOrphan
     */
    public void flush(int size, boolean hardFlush, boolean doOrphan) {

        if (parent != null)
            return;

        this.addUses();

        boolean needRepopulateUTX = true;

        pointClear = System.currentTimeMillis();

        this.commitSize += 10;

        /**
         * if by Commit Size: 91 MB - chain.dat.t = 2 GB !!
         * по размеру файла смотрим - если уже большой то сольем
         */
        if (commitSize > 20123123) {
            File dbFileT = new File(Settings.getInstance().getDataChainPath(), "chain.dat.t");
            if (dbFileT.exists()) {
                long sizeT = dbFileT.length();
                if (sizeT > 750000123) {
                    commitSize = sizeT;
                }
            }
        }

        if (hardFlush
                || actions > ACTIONS_BEFORE_COMMIT
                || commitSize > MAX_ENGINE_BEFORE_COMMIT
                || System.currentTimeMillis() - pointFlush > BlockChain.GENERATING_MIN_BLOCK_TIME_MS(BlockChain.VERS_30SEC + 1) << 8
        ) {

            long start = System.currentTimeMillis();

            log.debug("%%%%%%%%%%%%%%%%%%%% FLUSH %%%%%%%%%%%%%%%%%%%%");
            log.debug("%%%%%%%%%%%%%%%%%%%% "
                    + (hardFlush ? "by Command" : this.actions > ACTIONS_BEFORE_COMMIT ? "by Actions: " + this.actions :
                    (commitSize > MAX_ENGINE_BEFORE_COMMIT ? "by Commit Size: " + (commitSize >> 20) + " MB" : "by time"))
            );

            for (DBTab tab : tables) {
                tab.commit();
            }

            this.database.commit();
            //database.

            if (false && Controller.getInstance().compactDConStart && System.currentTimeMillis() - pointCompact > 9999999) {
                // очень долго делает - лучше ключом при старте
                pointCompact = System.currentTimeMillis();

                log.debug("try COMPACT");
                // очень долго делает - лучше ключом при старте
                try {
                    this.database.compact();
                    transactionTab.setTotalDeleted(0);
                    log.debug("COMPACTED");
                } catch (Exception e) {
                    transactionTab.setTotalDeleted(transactionTab.getTotalDeleted() >> 1);
                    log.error(e.getMessage(), e);
                }
            }

            if (true) {
                // Нельзя папку с базами форков чистить между записями блоков
                // так как еще другие процессы с форками есть - например создание своих транзакций или своего блока
                // они же тоже тут создаются,
                // а хотя тогда они и не удалятся - так как они не закрытые и останутся в папке... значит можно тут удалять - нужные не удаляться
                try {

                    // там же лежит и он
                    ///transactionTab.close();

                    // удалим все в папке Temp
                    File tempDir = new File(Settings.getInstance().getDataTempDir());
                    if (tempDir.exists()) {
                        Files.walkFileTree(tempDir.toPath(), new SimpleFileVisitorForRecursiveFolderDeletion());
                    }
                } catch (Throwable e) {
                    ///log.error(e.getMessage(), e);
                }

            }

            clearGC = !clearGC;
            if (clearGC) {
                if (true || needClearCache || clearGC) {
                    log.debug("CLEAR ENGINE CACHE...");
                    clearCache();
                }
                log.debug("CLEAR GC");
                System.gc();
            }

            log.info("%%%%%%%%%%%%%%%%%%%%%%%%  commit time: "
                    + (System.currentTimeMillis() - start) + " ms");

            pointFlush = System.currentTimeMillis();
            this.actions = 0L;
            this.commitSize = 0L;

        }

        this.outUses();
    }

    public String toString() {
        return (this.isFork() ? "forked in " + makedIn : "") + super.toString();
    }


}
