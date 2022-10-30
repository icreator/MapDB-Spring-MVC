package com.boots.repository.dbs.rocksDB.indexes.indexByteables;

import java.nio.charset.StandardCharsets;

public class IndexByteableString implements IndexByteable<String,Long> {
    @Override
    public byte[] toBytes(String result) {
        return result.getBytes(StandardCharsets.UTF_8);
    }
}
