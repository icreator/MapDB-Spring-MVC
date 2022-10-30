package com.boots.entity;

import com.boots.repository.db_a.DCSet_A;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.json.simple.JSONObject;
import org.mapdb.Fun;
import org.parboiled.common.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SEE in concrete TRANSACTIONS
 * public static final byte[][] VALID_RECORDS = new byte[][]{
 * };
 */

public abstract class Transaction {


    // toBYTE & PARSE fields for different DEALs
    public static final int FOR_MYPACK = 1; // not use this.timestamp & this.feePow
    public static final int FOR_PACK = 2; // not use feePow
    public static final int FOR_NETWORK = 3; // use all (but not calcalated)
    public static final int FOR_DB_RECORD = 4; // use all + calcalated fields (FEE, BlockNo + SeqNo)


    // VALIDATION CODE
    public static final int JSON_ERROR = -1;
    public static final int VALIDATE_OK = 1;
    public static final int FUTURE_ABILITY = 2;

    //
    // TYPES *******
    // universal
    public static final int EXTENDED = 1;
    // ISSUE ITEMS
    public static final int ISSUE_ASSET_TRANSACTION = 21;
    public static final int ISSUE_IMPRINT_TRANSACTION = 22;
    public static final int ISSUE_TEMPLATE_TRANSACTION = 23;
    public static final int ISSUE_PERSON_TRANSACTION = 24;
    public static final int ISSUE_STATUS_TRANSACTION = 25;
    public static final int ISSUE_UNION_TRANSACTION = 26;
    public static final int ISSUE_STATEMENT_TRANSACTION = 27;
    public static final int ISSUE_POLL_TRANSACTION = 28;
    // SEND ASSET
    public static final int SEND_ASSET_TRANSACTION = 31;
    // OTHER
    public static final int SIGN_NOTE_TRANSACTION = 35;
    public static final int CERTIFY_PUB_KEYS_TRANSACTION = 36;
    public static final int SET_STATUS_TO_ITEM_TRANSACTION = 37;
    public static final int SET_UNION_TO_ITEM_TRANSACTION = 38;
    public static final int SET_UNION_STATUS_TO_ITEM_TRANSACTION = 39;
    // confirm other transactions
    public static final int SIGN_TRANSACTION = 40;
    // HASHES
    public static final int HASHES_RECORD = 41;

    public static final int ISSUE_ASSET_SERIES_TRANSACTION = 42;

    // exchange of assets
    public static final int CREATE_ORDER_TRANSACTION = 50;
    public static final int CANCEL_ORDER_TRANSACTION = 51;
    public static final int CHANGE_ORDER_TRANSACTION = 52;
    // voting
    public static final int CREATE_POLL_TRANSACTION = 61;
    public static final int VOTE_ON_POLL_TRANSACTION = 62;
    public static final int VOTE_ON_ITEM_POLL_TRANSACTION = 63;
    public static final int RELEASE_PACK = 70;

    public static final int CALCULATED_TRANSACTION = 100;

    // old
    public static final int ARBITRARY_TRANSACTION = 12 + 130;
    public static final int MULTI_PAYMENT_TRANSACTION = 13 + 130;
    public static final int DEPLOY_AT_TRANSACTION = 14 + 130;

    // FEE PARAMETERS
    public static final long RIGHTS_KEY = 1L;
    public static final long FEE_KEY = 1L;

    public static final int TIMESTAMP_LENGTH = 8;
    public static final int SEQ_NO_LENGTH = 8;

    public static final int FLAGS_LENGTH = TIMESTAMP_LENGTH;

    public static final int KEY_LENGTH = 8;
    public static final int SIGNATURE_LENGTH = 64;

    // PROPERTIES LENGTH
    protected static final int SIMPLE_TYPE_LENGTH = 1;
    public static final int TYPE_LENGTH = 4;
    protected static final int HEIGHT_LENGTH = 4;
    public static final int DATA_JSON_PART_LENGTH = 4;
    public static final int DATA_VERSION_PART_LENGTH = 6;
    public static final int DATA_TITLE_PART_LENGTH = 4;
    protected static final int DATA_NUM_FILE_LENGTH = 4;
    protected static final int SEQ_LENGTH = Integer.BYTES;
    public static final int DBREF_LENGTH = Long.BYTES;
    public static final int DATA_SIZE_LENGTH = Integer.BYTES;
    public static final int ENCRYPTED_LENGTH = 1;
    public static final int IS_TEXT_LENGTH = 1;
    protected static final int FEE_POWER_LENGTH = 1;
    public static final int FEE_LENGTH = 8;
    public static final int CREATOR_LENGTH = 32;
    protected static final int BASE_LENGTH_AS_MYPACK = TYPE_LENGTH;
    protected static final int BASE_LENGTH_AS_PACK = BASE_LENGTH_AS_MYPACK + TIMESTAMP_LENGTH
            + CREATOR_LENGTH + SIGNATURE_LENGTH;
    protected static final int BASE_LENGTH = BASE_LENGTH_AS_PACK + FEE_POWER_LENGTH + FLAGS_LENGTH;
    protected static final int BASE_LENGTH_AS_DBRECORD = BASE_LENGTH + TIMESTAMP_LENGTH + FEE_LENGTH;

    static Logger LOGGER = LoggerFactory.getLogger(Transaction.class.getName());

    protected DCSet_A dcSet;
    protected String TYPE_NAME = "unknown";

    /////////   MASKS and PARS
    public static final byte HAS_EXLINK_MASK = 32;
    public static final byte HAS_SMART_CONTRACT_MASK = 16;
    /**
     * typeBytes[2] | HAS_EXLINK_MASK | HAS_SMART_CONTRACT_MASK
     */
    protected byte[] typeBytes;

    protected int height;
    protected int seqNo;
    protected long dbRef; // height + SeqNo

    /**
     * external flags of transaction. If FLAGS is USED need to set SINB BIT - use
     */
    protected long extFlags = 0L;
    public static final long FLAGS_USED_MASK = Long.MIN_VALUE;


    protected BigDecimal fee = BigDecimal.ZERO; // - for genesis


    // transactions
    protected byte feePow = 0;
    protected byte[] signature;
    protected long timestamp;

    /**
     * Для создания поисковых Меток - Тип сущности + номер ее (например @P12 - персона 12) + Метки (Tags) от самой Сущности
     */
    protected Object[][] itemsKeys;

    protected ExLink exLink;
    protected DAPP dApp;

    /**
     * если да то значит взята из Пула трнзакций и на двойную трату проверялась
     */
    public boolean checkedByPool;

    public String errorValue;

    // need for genesis
    protected Transaction(byte type, String type_name) {
        this.typeBytes = new byte[]{type, 0, 0, 0}; // for GENESIS
        this.TYPE_NAME = type_name;
    }

    protected Transaction(byte[] typeBytes, String type_name, PublicKeyAccount creator, ExLink exLink, DAPP dApp, byte feePow, long timestamp,
                          long extFlags) {
        this.typeBytes = typeBytes;
        this.TYPE_NAME = type_name;
        this.creator = creator;
        if (exLink != null) {
            typeBytes[2] |= HAS_EXLINK_MASK;
            this.exLink = exLink;
        } else {
            typeBytes[2] &= ~HAS_EXLINK_MASK;
        }

        // NOT NEED HERE setup - typeBytes[2] | HAS_SMART_CONTRACT_MASK()
        this.dApp = dApp;

        this.timestamp = timestamp;

        this.extFlags = extFlags;

        if (feePow < 0)
            feePow = 0;
        else if (feePow > BlockChain.FEE_POW_MAX)
            feePow = BlockChain.FEE_POW_MAX;
        this.feePow = feePow;
    }

    /*
    protected Transaction(byte[] typeBytes, String type_name, PublicKeyAccount creator, ExLink exLink, byte feePow, long timestamp,
                          long flags, byte[] signature) {
        this(typeBytes, type_name, creator, exLink, null, feePow, timestamp, flags);
        this.signature = signature;
    }

     */

    public static int getVersion(byte[] typeBytes) {
        return Byte.toUnsignedInt(typeBytes[1]);
    }


    public static Transaction findByHeightSeqNo(DCSet_A db, int height, int seq) {
        return db.getTransactionFinalMap().get(height, seq);
    }

    @Override
    public int hashCode() {
        return Ints.fromByteArray(signature);
    }

    @Override
    public boolean equals(Object transaction) {
        if (transaction instanceof Transaction)
            return Arrays.equals(this.signature, ((Transaction) transaction).signature);
        return false;
    }

    public boolean trueEquals(Object transaction) {
        if (transaction == null)
            return false;
        else if (transaction instanceof Transaction)
            return Arrays.equals(this.toBytes(FOR_NETWORK, true),
                    ((Transaction) transaction).toBytes(FOR_NETWORK, true));
        return false;
    }

    // reference in Map - or as signatire or as BlockHeight + seqNo
    public static Transaction findByDBRef(DCSet_A db, byte[] dbRef) {

        if (dbRef == null)
            return null;

        Long key;
        if (dbRef.length > 20) {
            // soft or hard confirmations
            key = db.getTransactionFinalMapSigns().get(dbRef);
            if (key == null) {
                return db.getTransactionTab().get(dbRef);
            }
        } else {
            int heightBlock = Ints.fromByteArray(Arrays.copyOfRange(dbRef, 0, 4));
            int seqNo = Ints.fromByteArray(Arrays.copyOfRange(dbRef, 4, 8));
            key = Transaction.makeDBRef(heightBlock, seqNo);

        }

        return db.getTransactionFinalMap().get(key);

    }

    public static Map<String, Map<Long, BigDecimal>> subAssetAmount(Map<String, Map<Long, BigDecimal>> allAssetAmount,
                                                                    String address, Long assetKey, BigDecimal amount) {
        return addAssetAmount(allAssetAmount, address, assetKey, BigDecimal.ZERO.subtract(amount));
    }

    public static Map<String, Map<Long, BigDecimal>> addAssetAmount(Map<String, Map<Long, BigDecimal>> allAssetAmount,
                                                                    String address, Long assetKey, BigDecimal amount) {
        Map<String, Map<Long, BigDecimal>> newAllAssetAmount;
        if (allAssetAmount != null) {
            newAllAssetAmount = new LinkedHashMap<String, Map<Long, BigDecimal>>(allAssetAmount);
        } else {
            newAllAssetAmount = new LinkedHashMap<String, Map<Long, BigDecimal>>();
        }

        Map<Long, BigDecimal> newAssetAmountOfAddress;

        if (!newAllAssetAmount.containsKey(address)) {
            newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>();
            newAssetAmountOfAddress.put(assetKey, amount);

            newAllAssetAmount.put(address, newAssetAmountOfAddress);
        } else {
            if (!newAllAssetAmount.get(address).containsKey(assetKey)) {
                newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>(newAllAssetAmount.get(address));
                newAssetAmountOfAddress.put(assetKey, amount);

                newAllAssetAmount.put(address, newAssetAmountOfAddress);
            } else {
                newAssetAmountOfAddress = new LinkedHashMap<Long, BigDecimal>(newAllAssetAmount.get(address));
                BigDecimal newAmount = newAllAssetAmount.get(address).get(assetKey).add(amount);
                newAssetAmountOfAddress.put(assetKey, newAmount);

                newAllAssetAmount.put(address, newAssetAmountOfAddress);
            }
        }

        return newAllAssetAmount;
    }

    public static Map<String, Map<Long, Long>> addStatusTime(Map<String, Map<Long, Long>> allStatusTime, String address,
                                                             Long assetKey, Long time) {
        Map<String, Map<Long, Long>> newAllStatusTime;
        if (allStatusTime != null) {
            newAllStatusTime = new LinkedHashMap<String, Map<Long, Long>>(allStatusTime);
        } else {
            newAllStatusTime = new LinkedHashMap<String, Map<Long, Long>>();
        }

        Map<Long, Long> newStatusTimetOfAddress;

        if (!newAllStatusTime.containsKey(address)) {
            newStatusTimetOfAddress = new LinkedHashMap<Long, Long>();
            newStatusTimetOfAddress.put(assetKey, time);

            newAllStatusTime.put(address, newStatusTimetOfAddress);
        } else {
            if (!newAllStatusTime.get(address).containsKey(assetKey)) {
                newStatusTimetOfAddress = new LinkedHashMap<Long, Long>(newAllStatusTime.get(address));
                newStatusTimetOfAddress.put(assetKey, time);

                newAllStatusTime.put(address, newStatusTimetOfAddress);
            } else {
                newStatusTimetOfAddress = new LinkedHashMap<Long, Long>(newAllStatusTime.get(address));
                Long newTime = newAllStatusTime.get(address).get(assetKey) + time;
                newStatusTimetOfAddress.put(assetKey, newTime);

                newAllStatusTime.put(address, newStatusTimetOfAddress);
            }
        }

        return newAllStatusTime;
    }

    // GETTERS/SETTERS

    public void setHeightSeq(long seqNo) {
        this.dbRef = seqNo;
        this.height = parseHeightDBRef(seqNo);
        this.seqNo = (int) seqNo;
    }

    public void setHeightSeq(int height, int seqNo) {
        this.dbRef = makeDBRef(height, seqNo);
        this.height = height;
        this.seqNo = seqNo;
    }

    /**
     * need for sign
     *
     * @param dcSet
     */
    public void setHeightOrLast(DCSet_A dcSet) {

        if (this.height > 0)
            return;

        height = dcSet.getBlocksHeadsMap().size() + 1;
        this.seqNo = 1;
        this.dbRef = makeDBRef(height, seqNo);
    }

    public void setErrorValue(String value) {
        errorValue = value;
    }

    public static boolean isValidTransactionType(int type) {
        return !viewTypeName(type).equals("unknown");
    }

    public String getErrorValue() {
        return errorValue;
    }

    /**
     * NEED FOR DB SECONDATY KEYS see org.mapdb.Bind.secondaryKeys
     *
     * @param dcSet
     * @param andUpdateFromState если нужно нарастить мясо на скелет из базв Финал. Не нужно для неподтвержденных
     *                           и если ее нет в базе еще. Используется только для вычисления номера Сущности для отображения Выпускающих трнзакций - после их обработки, например в Блокэксплоере чтобы посмотреть какой актив был этой трнзакцией выпущен.
     */
    public void setDC(DCSet_A dcSet, boolean andUpdateFromState) {
        this.dcSet = dcSet;

        if (BlockChain.TEST_DB == 0 && creator != null) {
            creatorPersonDuration = creator.getPersonDuration(dcSet);
            if (creatorPersonDuration != null) {
                creatorPerson = (PersonCls) dcSet.getItemPersonMap().get(creatorPersonDuration.a);
            }
        }

        if (andUpdateFromState && !isWiped())
            updateFromStateDB();
    }

    public void setDC(DCSet_A dcSet) {
        setDC(dcSet, false);
    }

    /**
     * Нужно для наполнения данными для isValid & process
     *
     * @param dcSet
     * @param forDeal
     * @param blockHeight
     * @param seqNo
     * @param andUpdateFromState если нужно нарастить мясо на скелет из базв Финал. Не нужно для неподтвержденных
     *                           и если ее нет в базе еще. Используется только для вычисления номера Сущности для отображения Выпускающих трнзакций - после их обработки, например в Блокэксплоере чтобы посмотреть какой актив был этой трнзакцией выпущен.
     */
    public void setDC(DCSet_A dcSet, int forDeal, int blockHeight, int seqNo, boolean andUpdateFromState) {
        setDC(dcSet, false);
        this.height = blockHeight; //this.getBlockHeightByParentOrLast(dcSet);
        this.seqNo = seqNo;
        this.dbRef = Transaction.makeDBRef(height, seqNo);
        if (forDeal > Transaction.FOR_PACK && (this.fee == null || this.fee.signum() == 0))
            calcFee(true);

        if (andUpdateFromState && !isWiped())
            updateFromStateDB();
    }

    public void setDC(DCSet_A dcSet, int forDeal, int blockHeight, int seqNo) {
        setDC(dcSet, forDeal, blockHeight, seqNo, false);
    }

    /**
     * Нарастить мясо на скелет из базы состояния - нужно для:<br>
     * - записи в FinalMap b созданим вторичных ключей и Номер Сущности<br>
     * - для внесения в кошелек когда блок прилетел и из него сырые транзакции берем
     */
    public void updateFromStateDB() {
    }

    public boolean noDCSet_A() {
        return this.dcSet == null;
    }

    public DCSet_A getDCSet_A() {
        return this.dcSet;
    }

    public int getType() {
        return Byte.toUnsignedInt(this.typeBytes[0]);
    }

    public static Integer[] getTransactionTypes(boolean onlyUsed) {

        // SEND ASSET
        // OTHER
        // confirm other transactions
        // HASHES
        // exchange of assets
        // voting
        Integer[] list = new Integer[]{
                0,
                ISSUE_ASSET_TRANSACTION,
                ISSUE_IMPRINT_TRANSACTION,
                ISSUE_TEMPLATE_TRANSACTION,
                ISSUE_PERSON_TRANSACTION,
                ISSUE_STATUS_TRANSACTION,
                onlyUsed ? -1 : ISSUE_UNION_TRANSACTION,
                ISSUE_STATEMENT_TRANSACTION,
                ISSUE_POLL_TRANSACTION,

                // SEND ASSET
                SEND_ASSET_TRANSACTION,

                // OTHER
                SIGN_NOTE_TRANSACTION,
                CERTIFY_PUB_KEYS_TRANSACTION,
                SET_STATUS_TO_ITEM_TRANSACTION,
                onlyUsed ? -1 : SET_UNION_TO_ITEM_TRANSACTION,
                onlyUsed ? -1 : SET_UNION_STATUS_TO_ITEM_TRANSACTION,

                // confirm other transactions
                SIGN_TRANSACTION,

                // HASHES
                HASHES_RECORD,

                // exchange of assets
                CREATE_ORDER_TRANSACTION,
                CANCEL_ORDER_TRANSACTION,
                CHANGE_ORDER_TRANSACTION,

                ISSUE_ASSET_SERIES_TRANSACTION,

                // voting
                VOTE_ON_ITEM_POLL_TRANSACTION

        };

        if (onlyUsed) {
            ArrayList<Integer> tmp = new ArrayList<>();
            for (Integer type : list) {
                if (type < 0)
                    continue;
                tmp.add(type);
            }
            return tmp.toArray(new Integer[tmp.size()]);

        }
        return list;
    }

    public static String viewTypeName(int type) {
        switch (type) {

            // SEND ASSET
            case SEND_ASSET_TRANSACTION:
                return RSend.TYPE_NAME;


        }
        return "unknown";
    }

    public static String viewTypeName(int type, JSONObject langObj) {
        return Lang.T(viewTypeName(type), langObj);
    }

    public int getVersion() {
        return Byte.toUnsignedInt(this.typeBytes[1]);
    }

    public byte[] getTypeBytes() {
        return this.typeBytes;
    }

    public PublicKeyAccount getCreator() {
        return this.creator;
    }

    public List<PublicKeyAccount> getPublicKeys() {
        return null;
    }

    public Long getTimestamp() {
        return this.timestamp;
    }

    // for test signature only!!!
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDeadline() {
        return this.timestamp + 5 * BlockChain.UNCONFIRMED_DEADTIME_MS(this.timestamp);
    }

    public long getKey() {
        return 0L;
    }

    public Object[][] getItemsKeys() {
        if (itemsKeys == null)
            makeItemsKeys();

        return itemsKeys;
    }

    public long getAbsKey() {
        long key = this.getKey();
        if (key < 0)
            return -key;
        return key;
    }

    public String getTypeKey() {
        return "";
    }

    public BigDecimal getAmount() {
        return BigDecimal.ZERO;
    }

    public BigDecimal getAmount(Account account) {
        return BigDecimal.ZERO;
    }

    public BigDecimal getFee(Account account) {
        if (this.creator != null)
            if (this.creator.getAddress().equals(account))
                return this.fee;
        return BigDecimal.ZERO;
    }

    public BigDecimal getFee() {
        return this.fee;
    }

    public long getFeeLong() {
        return this.fee.unscaledValue().longValue();
    }

    public String getTitle() {
        return "";
    }

    public String getTitle(JSONObject langObj) {
        return getTitle();
    }

    public ExLink getExLink() {
        return exLink;
    }

    public DAPP getSmartContract() {
        return dApp;
    }

    public void resetEpochDAPP() {
        if (dApp != null && dApp.isEpoch()) {
            typeBytes[2] &= ~HAS_SMART_CONTRACT_MASK();
            dApp = null;
        }
    }

    public void makeItemsKeys() {
        if (isWiped()) {
            itemsKeys = new Object[][]{};
        }

        if (creatorPersonDuration != null) {
            itemsKeys = new Object[][]{
                    new Object[]{ItemCls.PERSON_TYPE, creatorPersonDuration.a, creatorPerson.getTags()}
            };
        }
    }

    public static String[] tags(int typeID, String type, String tags, String words, Object[][] itemsKeys) {

        String allTags = "@TT" + typeID;

        if (type != null)
            allTags += " " + type;

        if (tags != null)
            allTags += " " + tags;


        if (words != null)
            allTags += " " + words;

        String[] tagsWords = allTags.toLowerCase().split(SPLIT_CHARS);

        if (itemsKeys == null || itemsKeys.length == 0)
            return tagsWords;

        String[] tagsArray = new String[tagsWords.length + itemsKeys.length];

        System.arraycopy(tagsWords, 0, tagsArray, 0, tagsWords.length);
        List<String> exTagsList = new ArrayList();
        for (int i = tagsWords.length; i < tagsArray.length; i++) {
            try {
                Object[] itemKey = itemsKeys[i - tagsWords.length];
                tagsArray[i] = ItemCls.getItemTypeAndKey((int) itemKey[0], (Long) itemKey[1]).toLowerCase();
                // возможно там есть дополнительные метка
                if (// false && // пока отключим
                        typeID != CALCULATED_TRANSACTION && // все форжинговые и вычисляемые пропустим
                                itemKey.length > 2 && itemKey[2] != null) {
                    for (Object exTag : (Object[]) itemKey[2]) {
                        exTagsList.add(ItemCls.getItemTypeAndTag((int) itemKey[0], exTag.toString()).toLowerCase());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("itemsKeys[" + i + "] = " + itemsKeys[i - tagsWords.length].toString());
                throw (e);
            }
        }

        if (!exTagsList.isEmpty()) {
            exTagsList.addAll(Arrays.asList(tagsArray));
            tagsArray = exTagsList.toArray(tagsArray);
        }

        return tagsArray;
    }

    public String getExTags() {
        return null;
    }

    /**
     * При удалении - транзакция то берется из базы для создания индексов к удалению.
     * И она скелет - нужно базу данных задать и водтянуть номера сущностей и все заново просчитать чтобы правильно удалить метки.
     * Для этого проверку делаем в таблтцк при создании индексов
     *
     * @return
     */
    public String[] getTags() {

        if (itemsKeys == null)
            makeItemsKeys();

        try {
            return tags(getType(), viewTypeName(), getExTags(), getTitle(), itemsKeys);
        } catch (Exception e) {
            LOGGER.error(toString() + " - itemsKeys.len: " + itemsKeys.length);
            throw e;
        }
    }

    /*
     * public Long getReference() { return this.reference; }
     */

    public byte getFeePow() {
        return this.feePow;
    }

    public long getAssetKey() {
        return 0L;
    }

    public AssetCls getAsset() {
        return null;
    }

    public byte[] getSignature() {
        return this.signature;
    }

    public long getExtFlags() {
        return this.extFlags;
    }

    public List<byte[]> getOtherSignatures() {
        return null;
    }

    public static boolean checkIsFinal(DCSet_A dcSet, Transaction transaction) {
        Long dbRefFinal = dcSet.getTransactionFinalMapSigns().get(transaction.getSignature());
        if (dbRefFinal == null)
            return false;
        Tuple2<Integer, Integer> ref = parseDBRef(dbRefFinal);
        transaction.setDC(dcSet, FOR_DB_RECORD, ref.a, ref.b, true);

        return true;
    }

    /**
     * Постраничный поиск по строке поиска
     *
     * @param offest
     * @param filterStr
     * @param useForge
     * @param pageSize
     * @param fromID
     * @param fillFullPage
     * @return
     */
    public static Tuple3<Long, Long, List<Transaction>> searchTransactions(
            DCSet_A dcSet, String filterStr, boolean useForge, int pageSize, Long fromID, int offset, boolean fillFullPage) {

        List<Transaction> transactions = new ArrayList<>();

        TransactionFinalMapImpl map = dcSet.getTransactionFinalMap();

        if (filterStr != null && !filterStr.isEmpty()) {
            if (Base58.isExtraSymbols(filterStr)) {
                try {
                    Long dbRef = parseDBRef(filterStr);
                    if (dbRef != null) {
                        Transaction one = map.get(dbRef);
                        if (one != null) {
                            transactions.add(one);
                        }
                    }
                } catch (Exception e1) {
                }

            } else {
                try {
                    byte[] signature = Base58.decode(filterStr);
                    Transaction one = map.get(signature);
                    if (one != null) {
                        transactions.add(one);
                    }
                } catch (Exception e2) {
                }
            }
        }

        if (filterStr == null) {
            transactions = map.getTransactionsFromID(fromID, offset, pageSize, !useForge, fillFullPage);
        } else {
            transactions.addAll(map.getTransactionsByTitleFromID(filterStr, fromID,
                    offset, pageSize, fillFullPage));
        }

        if (transactions.isEmpty()) {
            // возможно вниз вышли за границу
            return new Tuple3<>(fromID, null, transactions);
        } else {
            return new Tuple3<>(
                    // включим ссылки на листание вверх
                    transactions.get(0).dbRef,
                    // это не самый конец - включим листание вниз
                    transactions.get(transactions.size() - 1).dbRef,
                    transactions);
        }

    }


    /**
     * Общий для всех проверка на допуск публичного сообщения
     *
     * @param title
     * @param data
     * @param isText
     * @param isEncrypted
     * @param message
     * @return
     */
    public static boolean hasPublicText(String title, byte[] data, boolean isText, boolean isEncrypted, String message) {
        String[] words = title.split(Transaction.SPLIT_CHARS);
        int length = 0;
        for (String word : words) {
            word = word.trim();
            if (Base58.isExtraSymbols(word)) {
                // все слова сложим по длинне
                length += word.length();
                if (length > (BlockChain.TEST_MODE ? 100 : 100))
                    return true;
            }
        }

        if ((data == null || data.length == 0) && (message == null || message.isEmpty()))
            return false;

        if (isText && !isEncrypted) {
            String text = message == null ? new String(data, StandardCharsets.UTF_8) : message;
            if ((text.contains(" ") || text.contains("_") || text.contains("-")) && text.length() > 100)
                return true;
        }
        return false;
    }

    /**
     * может ли быть трнзакция бесплатной?
     *
     * @return
     */
    public boolean isFreeFee() {
        return true;
    }

    public abstract boolean hasPublicText();

    public int getJobLevel() {
        return 0;
    }

    // get fee
    public long calcBaseFee(boolean withFreeProtocol) {
        int len = getFeeLength();
        if (withFreeProtocol && height > BlockChain.FREE_FEE_FROM_HEIGHT && seqNo <= BlockChain.FREE_FEE_TO_SEQNO
                && len < BlockChain.FREE_FEE_LENGTH) {
            // не учитываем комиссию если размер блока маленький
            return 0L;
        }

        return len * BlockChain.FEE_PER_BYTE;
    }

    // calc FEE by recommended and feePOW
    public void calcFee(boolean withFreeProtocol) {

        long fee_long = calcBaseFee(withFreeProtocol);
        if (fee_long == 0) {
            this.fee = BigDecimal.ZERO;
            return;
        }

        BigDecimal fee = new BigDecimal(fee_long).multiply(BlockChain.FEE_RATE).setScale(BlockChain.FEE_SCALE, BigDecimal.ROUND_UP);

        if (this.feePow > 0) {
            this.fee = fee.multiply(new BigDecimal(BlockChain.FEE_POW_BASE).pow(this.feePow)).setScale(BlockChain.FEE_SCALE, BigDecimal.ROUND_UP);
        } else {
            this.fee = fee;
        }
    }

    // GET forged FEE without invited FEE
    public long getForgedFee() {
        long fee = this.fee.unscaledValue().longValue();
        return fee - this.getInvitedFee() - this.getRoyaltyFee();
    }

    /**
     * Сколько на другие проценты уйдет - например создателю шаблона
     *
     * @return
     */
    public long getRoyaltyFee() {
        return 0L;
    }

    // GET only INVITED FEE
    public long getInvitedFee() {

        if (BlockChain.FEE_INVITED_DEEP <= 0 || !BlockChain.REFERAL_BONUS_FOR_PERSON(height)) {
            // SWITCH OFF REFERRAL
            return 0L;
        }

        Tuple4<Long, Integer, Integer, Integer> personDuration = creator.getPersonDuration(this.dcSet);
        if (personDuration == null
                || personDuration.a <= BlockChain.BONUS_STOP_PERSON_KEY) {
            // ANONYMOUS or ME
            return 0L;
        }

        long fee = this.fee.unscaledValue().longValue() - getRoyaltyFee();
        if (fee <= 0)
            return 0L;

        // Если слишком большая комиссия, то и награду чуток увеличим
        if (fee > BlockChain.BONUS_REFERAL << 4)
            return BlockChain.BONUS_REFERAL << 1;
        else if (fee < BlockChain.BONUS_REFERAL << 1) {
            // стандартно если обычная то половину отправим на подарки
            return fee >> 1;
        }

        // если повышенная то не будем изменять
        return BlockChain.BONUS_REFERAL;
    }

    public BigDecimal feeToBD(int fee) {
        return BigDecimal.valueOf(fee, BlockChain.FEE_SCALE);
    }

    public Tuple2<Integer, Integer> getHeightSeqNo() {
        return new Tuple2<Integer, Integer>(this.height, this.seqNo);
    }

    public int getBlockHeight() {

        if (this.height > 0)
            return this.height;

        return -1;
    }

    // get current or last
    public int getBlockHeightByParentOrLast(DCSet_A dc) {

        if (this.height > 0)
            return this.height;

        return dc.getBlocksHeadsMap().size() + 1;
    }

    public int getSeqNo() {
        return this.seqNo;
    }

    public long getDBRef() {
        return this.dbRef;
    }

    public byte[] getDBRefAsBytes() {
        return Longs.toByteArray(this.dbRef);
    }

    // reference in Map - or as signatire or as BlockHeight + seqNo
    public byte[] getDBRef(DCSet_A db) {
        if (this.getConfirmations(db) < BlockChain.MAX_ORPHAN) {
            // soft or hard confirmations
            return this.signature;
        }

        int bh = this.getBlockHeight();
        if (bh < 1)
            // not in chain
            return null;

        byte[] ref = Ints.toByteArray(bh);
        Bytes.concat(ref, Ints.toByteArray(this.getSeqNo()));
        return ref;

    }

    // reference in Map - or as signatire or as BlockHeight + seqNo
    public static Long makeDBRef(int height, int seqNo) {

        byte[] ref = Ints.toByteArray(height);
        return Longs.fromByteArray(Bytes.concat(ref, Ints.toByteArray(seqNo)));

    }

    public static Long makeDBRef(Tuple2<Integer, Integer> dbRef) {

        byte[] ref = Ints.toByteArray(dbRef.a);
        return Longs.fromByteArray(Bytes.concat(ref, Ints.toByteArray(dbRef.b)));

    }

    public static Long parseDBRef(String refStr) {
        if (refStr == null)
            return null;

        Long seqNo = parseDBRefSeqNo(refStr);
        if (seqNo != null)
            return seqNo;

        try {
            return Long.parseLong(refStr);
        } catch (Exception e1) {
        }

        return null;
    }

    public static Long parseDBRefSeqNo(String refStr) {
        if (refStr == null)
            return null;

        try {
            String[] strA = refStr.split("\\-");
            if (strA.length > 2)
                // это скорее всег время типа 2020-10-11
                return null;

            int height = Integer.parseInt(strA[0]);
            int seq = Integer.parseInt(strA[1]);
            byte[] ref = Ints.toByteArray(height);
            return Longs.fromByteArray(Bytes.concat(ref, Ints.toByteArray(seq)));
        } catch (Exception e) {
        }
        return null;
    }

    public static Tuple2<Integer, Integer> parseDBRef(Long dbRef) {

        byte[] bytes = Longs.toByteArray(dbRef);

        int blockHeight = Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
        int seqNo = Ints.fromByteArray(Arrays.copyOfRange(bytes, 4, 8));

        return new Tuple2<Integer, Integer>(blockHeight, seqNo);

    }

    public static int parseHeightDBRef(long dbRef) {
        return (int) (dbRef >> 32);
    }

    public boolean addCalculated(Block block, Account creator, long assetKey, BigDecimal amount,
                                 String message) {

        if (block != null) {
            block.addCalculated(creator, assetKey, amount,
                    message, this.dbRef);
            return true;
        }
        return false;
    }

    public Fun.Tuple4<Long, Integer, Integer, Integer> getCreatorPersonDuration() {
        return creatorPersonDuration;
    }

    public boolean isCreatorPersonalized() {
        return creatorPersonDuration != null;
    }

    ////
    // VIEW
    public String viewType() {
        return Byte.toUnsignedInt(typeBytes[0]) + "." + Byte.toUnsignedInt(typeBytes[1]);
    }

    public String viewTypeName() {
        return TYPE_NAME;
    }

    public String viewTypeName(JSONObject langObj) {
        return Lang.T(TYPE_NAME, langObj);
    }

    public String viewProperies() {
        return Byte.toUnsignedInt(typeBytes[2]) + "." + Byte.toUnsignedInt(typeBytes[3]);
    }

    public String viewSubTypeName() {
        return "";
    }

    public String viewSubTypeName(JSONObject langObj) {
        return "";
    }

    public String viewFullTypeName() {
        String sub = viewSubTypeName();
        return sub.isEmpty() ? Lang.T(viewTypeName()) : Lang.T(viewTypeName()) + ":" + Lang.T(sub);
    }

    public String viewFullTypeName(JSONObject langObj) {
        String sub = viewSubTypeName(langObj);
        return sub.isEmpty() ? viewTypeName(langObj) : viewTypeName(langObj) + ":" + sub;
    }

    public static String viewDBRef(long dbRef) {

        byte[] bytes = Longs.toByteArray(dbRef);

        int blockHeight = Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
        int seqNo = Ints.fromByteArray(Arrays.copyOfRange(bytes, 4, 8));

        return blockHeight + "-" + seqNo;

    }

    public static String viewDBRef(int blockHeight, int seqNo) {
        return blockHeight + "-" + seqNo;
    }

    public String viewHeightSeq() {
        return this.height + "-" + this.seqNo;
    }

    public String viewCreator() {
        return viewAccount(creator);
    }

    public static String viewAccount(Account account) {
        return account == null ? "GENESIS" : account.getPersonAsString();
    }

    public String viewRecipient() {
        return "";
    }

    /*
     * public String viewReference() { //return
     * reference==null?"null":Base58.encode(reference); return
     * reference==null?"null":"" + reference; }
     */
    public String viewSignature() {
        return signature == null ? "null" : Base58.encode(signature);
    }

    public String viewTimestamp() {
        return viewTimestamp(timestamp);
    }

    public static String viewTimestamp(long timestamp) {
        return timestamp < 1000 ? "null" : DateTimeFormat.timestamptoString(timestamp);
    }

    public int viewSize(int forDeal) {
        return getDataLength(forDeal, true);
    }

    // PARSE/CONVERT

    public String viewFeeLong() {
        return feePow + ":" + this.fee.unscaledValue().longValue();
    }

    public String viewFeeAndFiat(int fontSize) {

        int imgSize = (int) (1.4 * fontSize);
        String fileName = "images" + File.separator + "icons" + File.separator + "assets" + File.separator + AssetCls.FEE_NAME + ".png";
        String text = "<span style='vertical-align: 10px; font-size: 1.4em' ><b>" + fee.toString() + "</b>"
                + "<img width=" + imgSize + " height=" + imgSize
                + " src='file:" + fileName + "'></span>";

        boolean useDEX = Settings.getInstance().getCompuRateUseDEX();

        AssetCls assetRate = Controller.getInstance().getAsset(Settings.getInstance().getCompuRateAsset());
        if (assetRate == null)
            assetRate = Controller.getInstance().getAsset(95); // ISO-USD

        if (assetRate == null)
            assetRate = Controller.getInstance().getAsset(1L); // ERA

        BigDecimal compuRate;
        if (useDEX) {
            Trade lastTrade = DCSet_A.getInstance().getTradeMap().getLastTrade(AssetCls.FEE_KEY, assetRate.getKey(), false);
            if (lastTrade == null) {
                compuRate = BigDecimal.ZERO;
            } else {
                compuRate = lastTrade.getHaveKey() == AssetCls.FEE_KEY ? lastTrade.calcPriceRevers() : lastTrade.calcPrice();
            }

        } else {
            compuRate = new BigDecimal(Settings.getInstance().getCompuRate());
        }

        if (compuRate.signum() > 0) {
            BigDecimal fee_fiat = fee.multiply(compuRate).setScale(assetRate.getScale(), BigDecimal.ROUND_HALF_UP);
            if (assetRate.getKey() != AssetCls.FEE_KEY) {
                text += " (" + fee_fiat.toString();
                fileName = "images" + File.separator + "icons" + File.separator + "assets" + File.separator + assetRate.getName() + ".png";
                File file = new File(fileName);
                if (file.exists()) {
                    text += "<img width=" + imgSize + " height=" + imgSize
                            + " src='file:" + fileName + "'>";
                } else {
                    text += " " + assetRate.getTickerName();
                }

                text += ")";

            }
        }

        if (assetFEE != null && assetFEE.a.signum() != 0) {
            /// ASSET FEE
            AssetCls asset = this.getAsset();
            if (asset == null) {
                asset = Controller.getInstance().getAsset(getAbsKey());
            }

            text += "<br>" + Lang.T("Additional Asset FEE") + ": ";
            BigDecimal assetTax = BlockChain.ASSET_TRANSFER_PERCENTAGE(height, asset.getKey());
            BigDecimal assetTaxMin = BlockChain.ASSET_TRANSFER_PERCENTAGE_MIN(height, asset.getKey());
            text += viewAssetFee(asset, assetTax, assetTaxMin, assetFEE.a);
        }

        if (assetsPacketFEE != null && !assetsPacketFEE.isEmpty()) {
            /// ASSET FEE
            text += "<br>" + Lang.T("Additional Assets Package FEE") + ":";
            for (AssetCls packageAsset : assetsPacketFEE.keySet()) {
                BigDecimal assetTax = BlockChain.ASSET_TRANSFER_PERCENTAGE(height, packageAsset.getKey());
                BigDecimal assetTaxMin = BlockChain.ASSET_TRANSFER_PERCENTAGE_MIN(height, packageAsset.getKey());
                Tuple2<BigDecimal, BigDecimal> assetPacketFee = assetsPacketFEE.get(packageAsset);
                text += "<br>" + viewAssetFee(packageAsset, assetTax, assetTaxMin, assetPacketFee.a);
            }

        }

        return text;
    }

    public static String viewAssetFee(AssetCls asset, BigDecimal tax, BigDecimal minFee, BigDecimal result) {

        String text = result.stripTrailingZeros().toPlainString() + "[" + asset.viewName() + "] ("
                + "" + (tax == null ? "0" : tax.movePointRight(2).stripTrailingZeros().toPlainString())
                + "%, min: " + (minFee == null ? "0" : minFee.stripTrailingZeros().toPlainString()) + ")";
        return text;
    }

    public String viewItemName() {
        return "";
    }

    public String viewAmount() {
        return "";
    }

    public boolean hasLinkRecipients() {
        return false;
    }

    public DefaultMutableTreeNode viewLinksTree() {

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(this);

        exLink = getExLink();
        if (exLink != null) {
            Transaction parentTX = dcSet.getTransactionFinalMap().get(getExLink().getRef());
            ASMutableTreeNode parent = new ASMutableTreeNode(
                    Lang.T(exLink.viewTypeName(hasLinkRecipients())) + " "
                            + Lang.T("for # для"));
            parent.add(new DefaultMutableTreeNode(parentTX));
            root.add(parent);
        }

        try (IteratorCloseable<Tuple3<Long, Byte, Long>> iterator = dcSet.getExLinksMap()
                .getTXLinksIterator(dbRef, ExData.LINK_APPENDIX_TYPE, false)) {
            if (iterator.hasNext()) {
                ASMutableTreeNode list = new ASMutableTreeNode(Lang.T("Appendixes"));
                while (iterator.hasNext()) {
                    list.add(new DefaultMutableTreeNode(dcSet.getTransactionFinalMap().get(iterator.next().c)));
                }
                root.add(list);
            }
        } catch (IOException e) {
        }

        try (IteratorCloseable<Tuple3<Long, Byte, Long>> iterator = dcSet.getExLinksMap()
                .getTXLinksIterator(dbRef, ExData.LINK_SOURCE_TYPE, false)) {
            if (iterator.hasNext()) {
                ASMutableTreeNode list = new ASMutableTreeNode(Lang.T("Usage"));
                while (iterator.hasNext()) {
                    list.add(new DefaultMutableTreeNode(dcSet.getTransactionFinalMap().get(iterator.next().c)));
                }
                root.add(list);
            }
        } catch (IOException e) {
        }

        try (IteratorCloseable<Tuple3<Long, Byte, Long>> iterator = dcSet.getExLinksMap()
                .getTXLinksIterator(dbRef, ExData.LINK_REPLY_COMMENT_TYPE, false)) {
            if (iterator.hasNext()) {
                ASMutableTreeNode list = new ASMutableTreeNode(Lang.T("Replays and Comments"));
                while (iterator.hasNext()) {
                    list.add(new DefaultMutableTreeNode(dcSet.getTransactionFinalMap().get(iterator.next().c)));
                }
                root.add(list);
            }
        } catch (IOException e) {
        }

        if (root.isLeaf())
            return null;

        return root;
    }


    @SuppressWarnings("unchecked")
    protected JSONObject getJsonBase() {

        if (dcSet == null) {
            setDC(DCSet_A.getInstance(), true);
        }

        JSONObject transaction = new JSONObject();

        transaction.put("version", Byte.toUnsignedInt(this.typeBytes[1]));
        transaction.put("property1", Byte.toUnsignedInt(this.typeBytes[2]));
        transaction.put("property2", Byte.toUnsignedInt(this.typeBytes[3]));
        transaction.put("property1B", "0x" + Integer.toBinaryString(Byte.toUnsignedInt(this.typeBytes[2])));
        transaction.put("property2B", "0x" + Integer.toBinaryString(Byte.toUnsignedInt(this.typeBytes[3])));
        transaction.put("flags", extFlags);
        transaction.put("flagsB", "0x" + Long.toBinaryString(extFlags));

        transaction.put("confirmations", this.getConfirmations(dcSet));
        transaction.put("type", getType());
        transaction.put("record_type", this.viewTypeName());
        transaction.put("type_name", this.viewTypeName());
        transaction.put("sub_type_name", this.viewSubTypeName());

        if (exLink != null) {
            transaction.put("exLink", getExLink().toJson());
        }

        // getSignature - make in GENEIS
        transaction.put("signature", this.getSignature() == null ? "null" : Base58.encode(this.signature));

        //int height;
        if (height == 1) {
            transaction.put("creator", "genesis");
        } else if (this.creator == null) {
            transaction.put("creator", "genesis");
        } else {
            transaction.put("feePow", getFeePow());
            transaction.put("forgedFee", getForgedFee());
            transaction.put("royaltyFee", getRoyaltyFee());
            transaction.put("invitedFee", getInvitedFee());
            transaction.put("title", getTitle());
            transaction.put("deadLine", getDeadline());
            transaction.put("publickey", Base58.encode(this.creator.getPublicKey()));
            creator.toJsonPersonInfo(transaction, "creator");
            if (fee != null && fee.signum() != 0)
                transaction.put("fee", this.fee.stripTrailingZeros().toPlainString());
            transaction.put("timestamp", this.timestamp < 1000 ? "null" : this.timestamp);
        }

        if (this.height > 0) {
            transaction.put("height", this.height);
            transaction.put("sequence", this.seqNo);
            transaction.put("seqNo", viewHeightSeq());
            if (isWiped()) {
                transaction.put("wiped", true);
            }
        }

        if (assetFEE != null) {
            JSONObject jsonFee = new JSONObject();
            jsonFee.put("fee", assetFEE.a.stripTrailingZeros().toPlainString());
            if (assetFEE.b != null)
                jsonFee.put("burn", assetFEE.b.stripTrailingZeros().toPlainString());
            transaction.put("assetFEE", jsonFee);
        }

        if (assetsPacketFEE != null && !assetsPacketFEE.isEmpty()) {
            Tuple2<BigDecimal, BigDecimal> rowTAX;
            JSONObject jsonFee = new JSONObject();
            for (AssetCls asset : assetsPacketFEE.keySet()) {
                rowTAX = assetsPacketFEE.get(asset);
                JSONObject assetFee = new JSONObject();
                assetFee.put("fee", rowTAX.a.stripTrailingZeros().toPlainString());
                if (rowTAX.b != null)
                    assetFee.put("burn", rowTAX.b.stripTrailingZeros().toPlainString());
                jsonFee.put(asset.getKey(), assetFee);
            }
            transaction.put("assetPackageFEE", jsonFee);

        }

        transaction.put("size", this.viewSize(Transaction.FOR_NETWORK));

        transaction.put("tags", Arrays.asList(this.getTags()));

        return transaction;
    }

    public JSONObject jsonForExplorerPage(JSONObject langObj, Object[] args) {
        return toJson();
    }


    public abstract JSONObject toJson();


    // VALIDATE

    // releaserReference == null - not as pack
    // releaserReference = reference of releaser - as pack
    public byte[] toBytes(int forDeal, boolean withSignature) {

        //boolean asPack = releaserReference != null;

        byte[] data = new byte[0];

        // WRITE TYPE
        data = Bytes.concat(data, this.typeBytes);

        if (forDeal > FOR_MYPACK) {
            // WRITE TIMESTAMP
            byte[] timestampBytes = Longs.toByteArray(this.timestamp);
            data = Bytes.concat(data, timestampBytes);
        }

        if (typeBytes[0] != ISSUE_IMPRINT_TRANSACTION) {
            byte[] flagsBytes = Longs.toByteArray(this.extFlags);
            data = Bytes.concat(data, flagsBytes);
        }

        // WRITE CREATOR
        data = Bytes.concat(data, this.creator.getPublicKey());

        if ((typeBytes[2] & HAS_EXLINK_MASK) > 0) {
            data = Bytes.concat(data, exLink.toBytes());
        }

        if (dApp != null && (forDeal == FOR_DB_RECORD || !dApp.isEpoch())) {
            typeBytes[2] |= HAS_SMART_CONTRACT_MASK();
            data = Bytes.concat(data, dApp.toBytes(forDeal));
        } else {
            typeBytes[2] &= ~HAS_SMART_CONTRACT_MASK();
        }

        if (forDeal > FOR_PACK) {
            // WRITE FEE POWER
            byte[] feePowBytes = new byte[1];
            feePowBytes[0] = this.feePow;
            data = Bytes.concat(data, feePowBytes);
        }

        // SIGNATURE
        if (withSignature) {
            assert (this.signature.length == 64);
            data = Bytes.concat(data, this.signature);
        }

        if (forDeal == FOR_DB_RECORD) {
            // WRITE DBREF
            byte[] dbRefBytes = Longs.toByteArray(this.dbRef);
            data = Bytes.concat(data, dbRefBytes);

            // WRITE FEE
            byte[] feeBytes = Longs.toByteArray(this.fee.unscaledValue().longValue());
            data = Bytes.concat(data, feeBytes);
        }

        return data;

    }

    /**
     * Transaction bytes Length for calc FEE
     *
     * @return
     */
    public int getFeeLength() {
        int len = getDataLength(Transaction.FOR_NETWORK, true);

        len += BlockChain.ADD_FEE_BYTES_FOR_COMMON_TX;

        return len;
    }

    public int getDataLength(int forDeal, boolean withSignature) {
        // not include item reference

        int base_len;
        if (forDeal == FOR_MYPACK)
            base_len = BASE_LENGTH_AS_MYPACK;
        else if (forDeal == FOR_PACK)
            base_len = BASE_LENGTH_AS_PACK;
        else if (forDeal == FOR_DB_RECORD)
            base_len = BASE_LENGTH_AS_DBRECORD;
        else
            base_len = BASE_LENGTH;

        if (!withSignature)
            base_len -= SIGNATURE_LENGTH;

        if (exLink != null)
            base_len += exLink.length();

        if (dApp != null) {
            if (forDeal == FOR_DB_RECORD || !dApp.isEpoch()) {
                base_len += dApp.length(forDeal);
            }
        }

        return base_len;

    }


    /**
     * flags
     * = 1 - not check fee
     * = 2 - not check person
     * = 4 - not check PublicText
     */
    public int isValid(int forDeal, long checkFlags) {

        return VALIDATE_OK;

    }


    // ПРОЫЕРЯЛОСЬ! действует в совокупе с Финализе в Блоке
    @Override
    protected void finalize() throws Throwable {
        dcSet = null;
        super.finalize();
    }

    @Override
    public String toString() {
        if (height > 0) {
            return viewHeightSeq() + "(" + viewTypeName() + ") " + getTitle();
        }

        if (signature == null) {
            return "(" + viewTypeName() + ") " + getTitle();
        }
        return "(" + viewTypeName() + ") " + getTitle() + " - " + Base58.encode(signature);
    }

}
