package org.erachain.dbs.rocksDB;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import lombok.extern.slf4j.Slf4j;
import org.erachain.controller.Controller;
import org.erachain.core.account.Account;
import org.erachain.core.transaction.Transaction;
import org.erachain.database.DBASet;
import org.erachain.datachain.DCSet;
import org.erachain.datachain.TransactionFinalMap;
import org.erachain.datachain.TransactionFinalSuit;
import org.erachain.dbs.IteratorCloseable;
import org.erachain.dbs.rocksDB.common.RocksDbSettings;
import org.erachain.dbs.rocksDB.indexes.ArrayIndexDB;
import org.erachain.dbs.rocksDB.indexes.ListIndexDB;
import org.erachain.dbs.rocksDB.indexes.SimpleIndexDB;
import org.erachain.dbs.rocksDB.integration.DBRocksDBTableDBCommitedAsBath;
import org.erachain.dbs.rocksDB.transformation.ByteableLong;
import org.erachain.dbs.rocksDB.transformation.ByteableTransaction;
import org.mapdb.DB;
import org.rocksdb.ReadOptions;
import org.rocksdb.WriteOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
public class TransactionFinalSuitRocksDB extends DBMapSuit<Long, Transaction> implements TransactionFinalSuit
{

    private final String NAME_TABLE = "TRANSACTION_FINAL_TABLE";
    private final String typeTransactionsIndexName = "typeTxs";
    private final String senderTransactionsIndexName = "senderTxs";
    private final String recipientTransactionsIndexName = "recipientTxs";
    private final String dialogTransactionsIndexName = "dialogTxs";
    private final String addressTypeTransactionsIndexName = "addressTypeTxs";
    private final String titleIndexName = "titleTypeTxs";

    SimpleIndexDB<Long, Transaction, byte[]> typeTxs;
    SimpleIndexDB<Long, Transaction, byte[]> creatorTxs;
    ListIndexDB<Long, Transaction, byte[]> recipientTxs;
    ArrayIndexDB<Long, Transaction, byte[]> dialogTxs;

    /**
     * С учетом Создатель или Получатель (1 или 0)
     */
    ListIndexDB<Long, Transaction, byte[]> addressTypeTxs;
    ArrayIndexDB<Long, Transaction, byte[]> titleIndex;
    // TODO ужадить его и взять вместо него - addressTypeTxs - как в MapDB
    ListIndexDB<Long, Transaction, byte[]> addressBiDirectionTxs;

    public TransactionFinalSuitRocksDB(DBASet databaseSet, DB database, boolean sizeEnable) {
        super(databaseSet, database, logger, sizeEnable);
    }

    @Override
    public void openMap() {

        map = new DBRocksDBTableDBCommitedAsBath<>(new ByteableLong(), new ByteableTransaction(), NAME_TABLE, indexes,
                RocksDbSettings.initCustomSettings(7, 64, 32,
                        256, 10,
                        1, 256, 32, false),
                new WriteOptions().setSync(true).setDisableWAL(false),
                new ReadOptions(),
                databaseSet, sizeEnable);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void createIndexes() {

        // USE counter index
        indexes = new ArrayList<>();

        // теперь это протокольный для множественных выплат
        creatorTxs = new SimpleIndexDB<>(senderTransactionsIndexName,
                (aLong, transaction) -> {
                    Account account = transaction.getCreator();
                    if (account == null)
                        return null;
                    byte[] addressKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
                    System.arraycopy(account.getShortAddressBytes(), 0, addressKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
                    return addressKey;
                }, (result) -> result);
        indexes.add(creatorTxs);

        // теперь это протокольный для множественных выплат
        addressTypeTxs = new ListIndexDB<>(addressTypeTransactionsIndexName,
                (aLong, transaction) -> {

                    // NEED set DCSet for calculate getRecipientAccounts in RVouch for example
                    if (transaction.noDCSet()) {
                        transaction.setDC((DCSet) databaseSet, true);
                    }

                    Integer type = transaction.getType();
                    List<byte[]> addressesTypes = new ArrayList<>();
                    for (Account account : transaction.getInvolvedAccounts()) {
                        byte[] key = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2];
                        System.arraycopy(account.getShortAddressBytes(), 0, key, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
                        key[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
                        key[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (account.equals(transaction.getCreator()) ? 1 : 0);
                        addressesTypes.add(key);
                    }
                    return addressesTypes;
                },
                (result) -> result);
        indexes.add(addressTypeTxs);

        if (Controller.getInstance().onlyProtocolIndexing) {
            return;
        }

        typeTxs = new SimpleIndexDB<>(typeTransactionsIndexName,
                (aLong, transaction) -> {
                    return Ints.toByteArray(transaction.getType());
                }, (result) -> result);
        indexes.add(typeTxs);

        recipientTxs = new ListIndexDB<>(recipientTransactionsIndexName,
                (Long aLong, Transaction transaction) -> {
                    List<byte[]> recipients = new ArrayList<>();

                    // NEED set DCSet for calculate getRecipientAccounts in RVouch for example
                    if (transaction.noDCSet()) {
                        transaction.setDC((DCSet) databaseSet, true);
                    }

                    for (Account account : transaction.getRecipientAccounts()) {
                        byte[] addressKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
                        System.arraycopy(account.getShortAddressBytes(), 0, addressKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);

                        recipients.add(addressKey);
                    }
                    return recipients;
                }, (result) -> result);
        indexes.add(recipientTxs);

        dialogTxs = new ArrayIndexDB<>(dialogTransactionsIndexName,
                (Long aLong, Transaction transaction) -> {

                    // NEED set DCSet for calculate getRecipientAccounts in RVouch for example
                    if (transaction.noDCSet()) {
                        transaction.setDC((DCSet) databaseSet, true);
                    }

                    Account creator = transaction.getCreator();
                    if (creator == null)
                        return null;

                    HashSet<Account> recipients = transaction.getRecipientAccounts();
                    if (recipients == null || recipients.isEmpty())
                        return null;

                    int size = recipients.size();
                    byte[][] keys = new byte[size][];

                    int count = 0;
                    for (Account recipient : recipients) {
                        keys[count++] = TransactionFinalMap.makeDialogKey(creator, recipient);
                    }

                    return keys;
                }, (result) -> result);
        indexes.add(dialogTxs);

        titleIndex = new ArrayIndexDB<>(titleIndexName,
                (aLong, transaction) -> {

                    // NEED set DCSet for calculate getRecipientAccounts in RVouch for example
                    // При удалении - транзакция то берется из базы для создания индексов к удалению.
                    // И она скелет - нужно базу данных задать и водтянуть номера сущностей и все заново просчитать чтобы правильно удалить метки
                    if (transaction.noDCSet()) {
                        transaction.setDC((DCSet) databaseSet, true);
                    }

                    String[] tokens = transaction.getTags();
                    if (tokens == null || tokens.length == 0)
                        return null;

                    byte[][] keys = new byte[tokens.length][];
                    int count = 0;
                    for (String token : tokens) {
                        byte[] key = new byte[TransactionFinalMap.CUT_NAME_INDEX];
                        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
                        int keyLength = bytes.length;
                        if (keyLength >= TransactionFinalMap.CUT_NAME_INDEX) {
                            System.arraycopy(bytes, 0, key, 0, TransactionFinalMap.CUT_NAME_INDEX);
                            keys[count++] = key;
                        } else if (keyLength > 0) {
                            System.arraycopy(bytes, 0, key, 0, keyLength);
                            keys[count++] = key;
                        }
                    }

                    byte[][] keys2 = new byte[count][];
                    System.arraycopy(keys, 0, keys2, 0, keys2.length);
                    return keys2;

                }, (result) -> result);
        indexes.add(titleIndex);

        addressBiDirectionTxs = new ListIndexDB<>("addressBiDirectionTXIndex",
                (aLong, transaction) -> {

                    // NEED set DCSet for calculate getRecipientAccounts in RVouch for example
                    if (transaction.noDCSet()) {
                        transaction.setDC((DCSet) databaseSet, true);
                    }

                    List<byte[]> secondaryKeys = new ArrayList<>();
                    for (Account account : transaction.getInvolvedAccounts()) {
                        byte[] secondaryKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
                        System.arraycopy(account.getShortAddressBytes(), 0, secondaryKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
                        ////System.arraycopy(transaction.getDBRefAsBytes(), 0, secondaryKey, ADDRESS_KEY_LEN, 8);

                        secondaryKeys.add(secondaryKey);
                    }
                    return secondaryKeys;
                },
                (result) -> result
        );
        indexes.add(addressBiDirectionTxs);


    }

    // TODO  dbCore.deleteRange(beg, end);
    @Override
    public void deleteForBlock(Integer height) {
        try (IteratorCloseable<Long> iterator = getOneBlockIterator(height, true)) {
            while (iterator.hasNext()) {
                map.remove(iterator.next());
            }
        } catch (IOException e) {
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getOneBlockIterator(Integer height, boolean descending) {
        // GET ALL TRANSACTIONS THAT BELONG TO THAT ADDRESS
        //map.getIterator();
        return map.getIndexIteratorFilter(Ints.toByteArray(height), descending, false);

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByCreator(byte[] addressShort, boolean descending) {
        byte[] addressKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
        System.arraycopy(addressShort, 0, addressKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);

        return map.getIndexIteratorFilter(creatorTxs.getColumnFamilyHandle(), addressKey, descending, true);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByCreator(byte[] addressShort, Long fromSeqNo, boolean descending) {

        if (fromSeqNo == null) {
            byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            return map.getIndexIteratorFilter(creatorTxs.getColumnFamilyHandle(), fromKey, descending, true);
        }

        byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
        System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);

        return map.getIndexIteratorFilter(creatorTxs.getColumnFamilyHandle(), fromKey, null, descending, true);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByCreator(byte[] addressShort, Long fromSeqNo, Long toSeqNo, boolean descending) {

        byte[] fromKey;
        if (fromSeqNo == null) {
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        } else {
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);
        }

        byte[] toKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
        System.arraycopy(addressShort, 0, toKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        System.arraycopy(Longs.toByteArray(toSeqNo == null ? descending ? 0L : Long.MAX_VALUE : toSeqNo), 0, toKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);

        return map.getIndexIteratorFilter(creatorTxs.getColumnFamilyHandle(),
                fromKey, toKey, descending, true);

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByRecipient(byte[] addressShort, boolean descending) {
        byte[] addressKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
        System.arraycopy(addressShort, 0, addressKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);

        return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(), addressKey, descending, true);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByRecipient(byte[] addressShort, Long fromSeqNo, boolean descending) {

        if (fromSeqNo == null) {
            byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(), fromKey, descending, true);
        }

        byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
        System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);

        return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(), fromKey, null, descending, true);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorByRecipient(byte[] addressShort, Long fromSeqNo, Long toSeqNo, boolean descending) {

        byte[] fromKey;
        if (fromSeqNo == null) {
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        } else {
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);
        }

        byte[] toKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
        System.arraycopy(addressShort, 0, toKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        System.arraycopy(Longs.toByteArray(toSeqNo == null ? descending ? 0L : Long.MAX_VALUE : toSeqNo), 0, toKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);

        return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(),
                fromKey, toKey, descending, true);

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public IteratorCloseable<Long> getIteratorOfDialog(byte[] addressesKey, Long fromSeqNo, boolean descending) {

        // todo
        if (fromSeqNo == null) {
            byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressesKey, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(), fromKey, descending, true);
        }

        byte[] fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
        System.arraycopy(addressesKey, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);

        return map.getIndexIteratorFilter(recipientTxs.getColumnFamilyHandle(), fromKey, null, descending, true);
    }

    public IteratorCloseable<Long> getIteratorByType(Integer type, Long fromSeqNo, boolean descending) {

        // todo
        if (fromSeqNo == null) {
            byte[] fromKey = new byte[Integer.BYTES];
            System.arraycopy(Ints.toByteArray(type), 0, fromKey, 0, Integer.BYTES);
            return map.getIndexIteratorFilter(typeTxs.getColumnFamilyHandle(), fromKey, descending, true);
        }

        byte[] fromKey = new byte[Integer.BYTES + Long.BYTES];
        System.arraycopy(Ints.toByteArray(type), 0, fromKey, 0, Integer.BYTES);
        System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, Integer.BYTES, Long.BYTES);

        return map.getIndexIteratorFilter(typeTxs.getColumnFamilyHandle(), fromKey, null, descending, true);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    // TODO ERROR - not use PARENT MAP and DELETED in FORK
    public IteratorCloseable<Long> getIteratorByAddressAndType(byte[] addressShort, Integer type, Boolean isCreator, boolean descending) {

        if (type == null && isCreator != null) {
            return null;
        }

        byte[] key;
        if (type != null && type != 0) {
            if (isCreator != null) {
                key = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2];
                System.arraycopy(addressShort, 0, key, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
                key[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
                key[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (isCreator ? 1 : 0);
            } else {
                key = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 1];
                System.arraycopy(addressShort, 0, key, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
                key[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
            }
        } else {
            key = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, key, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        }
        return map.getIndexIteratorFilter(addressTypeTxs.getColumnFamilyHandle(), key, descending, true);

    }

    @Override
    public IteratorCloseable<Long> getIteratorByAddressAndType(byte[] addressShort, Integer type, Boolean isCreator, Long fromID, boolean descending) {
        if (fromID == null || isCreator == null || type == null) {
            return getIteratorByAddressAndType(addressShort, type, isCreator, descending);
        }

        if (type == null && isCreator != null) {
            return null;
        }

        byte[] keyFrom = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2 + Long.BYTES];
        System.arraycopy(addressShort, 0, keyFrom, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        keyFrom[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
        keyFrom[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (isCreator ? 1 : 0);
        System.arraycopy(Longs.toByteArray(fromID), 0, keyFrom, TransactionFinalMap.ADDRESS_KEY_LEN + 2, Long.BYTES);

        byte[] keyTo = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2 + Long.BYTES];
        System.arraycopy(addressShort, 0, keyTo, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        keyTo[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
        keyTo[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (isCreator ? 1 : 0);
        System.arraycopy(Longs.toByteArray(descending ? 0L : Long.MAX_VALUE),
                0, keyTo, TransactionFinalMap.ADDRESS_KEY_LEN + 2, Long.BYTES);

        return map.getIndexIteratorFilter(addressTypeTxs.getColumnFamilyHandle(),
                keyFrom, keyTo, descending, true);

    }

    @Override
    public IteratorCloseable<Long> getIteratorByAddressAndType(byte[] addressShort, Integer type, Boolean isCreator, Long fromID, Long toID, boolean descending) {
        if (fromID == null || isCreator == null || type == null) {
            return getIteratorByAddressAndType(addressShort, type, isCreator, descending);
        }

        if (type == null && isCreator != null) {
            return null;
        }

        byte[] keyFrom = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2 + Long.BYTES];
        System.arraycopy(addressShort, 0, keyFrom, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        keyFrom[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
        keyFrom[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (isCreator ? 1 : 0);
        System.arraycopy(Longs.toByteArray(fromID), 0, keyFrom, TransactionFinalMap.ADDRESS_KEY_LEN + 2, Long.BYTES);

        byte[] keyTo = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + 2 + Long.BYTES];
        System.arraycopy(addressShort, 0, keyTo, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        keyTo[TransactionFinalMap.ADDRESS_KEY_LEN] = (byte) (int) type;
        keyTo[TransactionFinalMap.ADDRESS_KEY_LEN + 1] = (byte) (isCreator ? 1 : 0);
        System.arraycopy(Longs.toByteArray(toID == null ? descending ? 0L : Long.MAX_VALUE : toID),
                0, keyTo, TransactionFinalMap.ADDRESS_KEY_LEN + 2, Long.BYTES);

        return map.getIndexIteratorFilter(addressTypeTxs.getColumnFamilyHandle(),
                keyFrom, keyTo, descending, true);

    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    // TODO ERROR - not use PARENT MAP and DELETED in FORK
    public IteratorCloseable<Long> getIteratorByTitle(String filter, boolean asFilter, String fromWord, Long fromSeqNo, boolean descending) {

        byte[] filterLower = filter.toLowerCase().getBytes(StandardCharsets.UTF_8);
        int filterLowerLength = Math.min(filterLower.length, TransactionFinalMap.CUT_NAME_INDEX);

        byte[] fromKey;
        byte[] toKey;

        if (fromWord == null) {
            // ищем все с самого начала для данного адреса
            //fromKey = new byte[filterLowerLength];
            //System.arraycopy(filterLower, 0, fromKey, 0, filterLowerLength);

            if (descending) {
                // тут нужно взять кранее верхнее значени и найти нижнее первое
                // см. https://github.com/facebook/rocksdb/wiki/SeekForPrev
                byte[] prevFilter;
                if (asFilter) {
                    // тут берем вообще все варианты
                    prevFilter = new byte[filterLowerLength + 1];
                    System.arraycopy(filterLower, 0, prevFilter, 0, filterLowerLength);
                    prevFilter[filterLowerLength] = (byte) 255;
                } else {
                    // тут берем только варианты обрезанные но со всеми номерами
                    prevFilter = new byte[TransactionFinalMap.CUT_NAME_INDEX + Long.BYTES];
                    System.arraycopy(filterLower, 0, prevFilter, 0, filterLowerLength);
                    System.arraycopy(Longs.toByteArray(Long.MAX_VALUE), 0, prevFilter, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);
                    //prevFilter[filterLowerLength] = (byte) 255; // все что меньше не берем
                }

                return map.getIndexIteratorFilter(titleIndex.getColumnFamilyHandle(),
                        prevFilter, filterLower, descending, true);

            } else {

                if (asFilter) {
                    toKey = new byte[filterLowerLength + 1];
                    System.arraycopy(filterLower, 0, toKey, 0, filterLowerLength);
                    toKey[filterLowerLength] = (byte) 255;
                } else {
                    // тут берем только варианты обрезанные но со всеми номерами
                    toKey = new byte[TransactionFinalMap.CUT_NAME_INDEX + Long.BYTES];
                    System.arraycopy(filterLower, 0, toKey, 0, filterLowerLength);
                    System.arraycopy(Longs.toByteArray(Long.MAX_VALUE), 0, toKey, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);
                }
                return map.getIndexIteratorFilter(titleIndex.getColumnFamilyHandle(),
                        filterLower, toKey, descending, true);

            }

        } else {

            // поиск ключа первого у всех одинаковый
            byte[] startFrom = fromWord.toLowerCase().getBytes(StandardCharsets.UTF_8);
            int startFromLength = Math.min(startFrom.length, TransactionFinalMap.CUT_NAME_INDEX);
            // используем полный ключ для начального поиска
            // значит и стартовое слово надо использовать
            fromKey = new byte[TransactionFinalMap.CUT_NAME_INDEX + Long.BYTES];
            System.arraycopy(startFrom, 0, fromKey, 0, startFromLength);

            if (descending) {

                // тут нужно взять кранее верхнее значени и найти нижнее первое
                // см. https://github.com/facebook/rocksdb/wiki/SeekForPrev
                /// учет на то что начальный ключ будет взят предыдущий от поиска + 1
                System.arraycopy(Longs.toByteArray(fromSeqNo // + 1L
                ), 0, fromKey, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);

                if (asFilter) {
                    // тут берем вообще все варианты
                    toKey = filterLower;
                } else {
                    // тут берем только варианты обрезанные но со всеми номерами
                    toKey = new byte[TransactionFinalMap.CUT_NAME_INDEX + Long.BYTES];
                    System.arraycopy(filterLower, 0, toKey, 0, filterLowerLength);
                    System.arraycopy(Longs.toByteArray(0L), 0, toKey, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);
                    toKey[filterLowerLength] = (byte) 255; // все что меньше не берем
                }

                return map.getIndexIteratorFilter(titleIndex.getColumnFamilyHandle(),
                        fromKey, toKey, descending, true);
            } else {

                System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);

                if (asFilter) {
                    // диаппазон заданим если у нас фильтр - значение начальное увеличим в нути ключа
                    toKey = new byte[filterLowerLength + 1];
                    System.arraycopy(filterLower, 0, toKey, 0, filterLowerLength);
                    toKey[filterLowerLength] = (byte) 255;
                } else {
                    toKey = new byte[TransactionFinalMap.CUT_NAME_INDEX + Long.BYTES];
                    System.arraycopy(filterLower, 0, toKey, 0, filterLowerLength);
                    System.arraycopy(Longs.toByteArray(Long.MAX_VALUE), 0, toKey, TransactionFinalMap.CUT_NAME_INDEX, Long.BYTES);
                }
                return map.getIndexIteratorFilter(titleIndex.getColumnFamilyHandle(),
                        fromKey, toKey, descending, true);
            }
        }
    }

    @Override
    public IteratorCloseable<Long> getAddressesIterator(byte[] addressShort, Long fromSeqNo, boolean descending) {

        if (addressShort == null)
            return getIterator(fromSeqNo, descending);

        byte[] fromKey;

        if (fromSeqNo == null || fromSeqNo == 0) {
            // ищем все с самого начала для данного адреса
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
        } else {
            // используем полный ключ для начального поиска
            fromKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN + Long.BYTES];
            System.arraycopy(addressShort, 0, fromKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);
            System.arraycopy(Longs.toByteArray(fromSeqNo), 0, fromKey, TransactionFinalMap.ADDRESS_KEY_LEN, Long.BYTES);
        }

        byte[] toKey = new byte[TransactionFinalMap.ADDRESS_KEY_LEN];
        System.arraycopy(addressShort, 0, toKey, 0, TransactionFinalMap.ADDRESS_KEY_LEN);

        if (descending && fromSeqNo == null) {
            // тут нужно взять кранее верхнее значени и найти нижнее первое
            // см. https://github.com/facebook/rocksdb/wiki/SeekForPrev
            int length = fromKey.length;
            byte[] prevFilter = new byte[length + 1];
            System.arraycopy(fromKey, 0, prevFilter, 0, length);
            prevFilter[length] = (byte) 255;

            //toKey =
            return map.getIndexIteratorFilter(addressBiDirectionTxs.getColumnFamilyHandle(),
                    prevFilter, toKey, descending, true);

        } else {
            return map.getIndexIteratorFilter(addressBiDirectionTxs.getColumnFamilyHandle(),
                    fromKey, toKey, descending, true);

        }

    }

}
