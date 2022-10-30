package com.boots.repository.dbs.rocksDB.transformation.lists;

import com.boots.repository.dbs.rocksDB.transformation.ByteableInteger;
import com.boots.repository.dbs.rocksDB.transformation.ByteableLong;
import com.boots.repository.dbs.rocksDB.transformation.tuples.ByteableTuple4;

public class ByteableStackTuple4 extends ByteableStack<Tuple4<Long, Integer, Integer, Integer>> {

    final static int LENGHT = Long.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES;
    ByteableLong byteableLong = new ByteableLong();
    ByteableInteger byteableInteger = new ByteableInteger();

    public ByteableStackTuple4() {
        ByteableTuple4<Long, Integer, Integer, Integer> byteableElement = new ByteableTuple4<Long, Integer, Integer, Integer>() {
            @Override
            public int[] sizeElements() {
                return new int[]{Long.BYTES, Integer.BYTES, Integer.BYTES, Integer.BYTES};
            }
        };
        byteableElement.setByteables(new Byteable[]{byteableLong, byteableInteger, byteableInteger, byteableInteger});
        setByteableElement(byteableElement);
    }


    @Override
    public int sizeElement() {
        return LENGHT;
    }


}
