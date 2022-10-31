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

        return transaction;
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

    @Override
    protected void finalize() throws Throwable {
        dcSet = null;
        super.finalize();
    }

}
