package com.boots.repository.dbs.rocksDB.transformation.tuples;

import lombok.Setter;
import org.erachain.dbs.rocksDB.transformation.Byteable;
import org.mapdb.Fun.Tuple4;

import java.util.Arrays;

@Setter
public abstract class ByteableTuple4<F0, F1, F2, F3> implements Byteable<Tuple4<F0, F1, F2, F3>> {


    public abstract int[] sizeElements();

    private Byteable[] byteables;


    @Override
    public Tuple4<F0, F1, F2, F3> receiveObjectFromBytes(byte[] bytes) {
        int[] limits = sizeElements();
        byte[] bytesF0 = Arrays.copyOfRange(bytes, 0, limits[0]);
        byte[] bytesF1 = Arrays.copyOfRange(bytes, limits[0], limits[0] + limits[1]);
        byte[] bytesF2 = Arrays.copyOfRange(bytes, limits[0] + limits[1], limits[0] + limits[1] + limits[2]);
        byte[] bytesF3 = Arrays.copyOfRange(bytes, limits[0] + limits[1] + limits[2], bytes.length);
        return new Tuple4(
                (F0) byteables[0].receiveObjectFromBytes(bytesF0),
                (F1) byteables[1].receiveObjectFromBytes(bytesF1),
                (F2) byteables[2].receiveObjectFromBytes(bytesF2),
                (F3) byteables[2].receiveObjectFromBytes(bytesF3));
    }

    @Override
    public byte[] toBytesObject(Tuple4<F0, F1, F2, F3> value) {
        return org.bouncycastle.util.Arrays.concatenate(
                byteables[0].toBytesObject(value.a),
                byteables[1].toBytesObject(value.b),
                byteables[2].toBytesObject(value.c),
                byteables[2].toBytesObject(value.d));
    }


}
