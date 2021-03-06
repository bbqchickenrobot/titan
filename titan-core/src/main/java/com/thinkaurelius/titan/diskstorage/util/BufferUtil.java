package com.thinkaurelius.titan.diskstorage.util;

import com.google.common.base.Preconditions;
import com.thinkaurelius.titan.diskstorage.*;
import com.thinkaurelius.titan.graphdb.database.idhandling.VariableLong;
import com.thinkaurelius.titan.graphdb.database.serialize.DataOutput;
import com.thinkaurelius.titan.graphdb.database.serialize.Serializer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

/**
 * Utility methods for dealing with {@link ByteBuffer}.
 *
 */
public class BufferUtil {

    public static final int longSize = StaticArrayBuffer.LONG_LEN;
    public static final int intSize = StaticArrayBuffer.INT_LEN;

    /* ###############
     * Simple StaticBuffer construction
     * ################
     */

    public static final StaticBuffer getIntBuffer(int id) {
        ByteBuffer buffer = ByteBuffer.allocate(intSize);
        buffer.putInt(id);
        byte[] arr = buffer.array();
        Preconditions.checkArgument(arr.length == intSize);
        return StaticArrayBuffer.of(arr);
    }

    public static final StaticBuffer getIntBuffer(int[] ids) {
        ByteBuffer buffer = ByteBuffer.allocate(intSize * ids.length);
        for (int i = 0; i < ids.length; i++)
            buffer.putInt(ids[i]);
        byte[] arr = buffer.array();
        Preconditions.checkArgument(arr.length == intSize * ids.length);
        return StaticArrayBuffer.of(arr);
    }

    public static final StaticBuffer getLongBuffer(long id) {
        ByteBuffer buffer = ByteBuffer.allocate(longSize);
        buffer.putLong(id);
        byte[] arr = buffer.array();
        Preconditions.checkArgument(arr.length == longSize);
        return StaticArrayBuffer.of(arr);
    }


    public static final StaticBuffer fillBuffer(int len, byte value) {
        byte[] res = new byte[len];
        for (int i = 0; i < len; i++) res[i]=value;
        return StaticArrayBuffer.of(res);
    }

    public static final StaticBuffer oneBuffer(int len) {
        return fillBuffer(len,(byte)-1);
    }

    public static final StaticBuffer zeroBuffer(int len) {
        return fillBuffer(len,(byte)0);
    }

    public static final StaticBuffer emptyBuffer() {
        return fillBuffer(0,(byte)0);
    }

    /* ################
     * Buffer I/O
     * ################
     */

    public static void writeEntry(DataOutput out, Entry entry) {
        VariableLong.writePositive(out,entry.getValuePosition());
        writeBuffer(out,entry);
        if (!entry.hasMetaData()) out.putByte((byte)0);
        else {
            Map<EntryMetaData,Object> metadata = entry.getMetaData();
            assert metadata.size()>0 && metadata.size()<Byte.MAX_VALUE;
            assert EntryMetaData.values().length<Byte.MAX_VALUE;
            out.putByte((byte)metadata.size());
            for (Map.Entry<EntryMetaData,Object> metas : metadata.entrySet()) {
                EntryMetaData meta = metas.getKey();
                out.putByte((byte)meta.ordinal());
                out.writeObjectNotNull(metas.getValue());
            }
        }
    }

    public static void writeBuffer(DataOutput out, StaticBuffer buffer) {
        VariableLong.writePositive(out,buffer.length());
        out.putBytes(buffer);
    }

    public static Entry readEntry(ReadBuffer in, Serializer serializer) {
        long valuePosition = VariableLong.readPositive(in);
        Preconditions.checkArgument(valuePosition>0 && valuePosition<=Integer.MAX_VALUE);
        StaticBuffer buffer = readBuffer(in);

        StaticArrayEntry entry = new StaticArrayEntry(buffer, (int) valuePosition);
        int metaSize = in.getByte();
        for (int i=0;i<metaSize;i++) {
            EntryMetaData meta = EntryMetaData.values()[in.getByte()];
            entry.setMetaData(meta,serializer.readObjectNotNull(in,meta.getDataType()));
        }
        return entry;
    }

    public static StaticBuffer readBuffer(ScanBuffer in) {
        long length = VariableLong.readPositive(in);
        Preconditions.checkArgument(length>=0 && length<=Integer.MAX_VALUE);
        byte[] data = in.getBytes((int)length);
        assert data.length==length;
        return new StaticArrayBuffer(data);
    }

    /* ################
     * StaticBuffer Manipulation
     * ################
     */


    public static final StaticBuffer nextBiggerBufferAllowOverflow(StaticBuffer buffer) {
        return nextBiggerBuffer(buffer, true);
    }

    public static final StaticBuffer nextBiggerBuffer(StaticBuffer buffer) {
        return nextBiggerBuffer(buffer,false);
    }

    private static final StaticBuffer nextBiggerBuffer(StaticBuffer buffer, boolean allowOverflow) {
        int len = buffer.length();
        byte[] next = new byte[len];
        boolean carry = true;
        for (int i = len - 1; i >= 0; i--) {
            byte b = buffer.getByte(i);
            if (carry) {
                b++;
                if (b != 0) carry = false;
            }
            next[i]=b;
        }
        if (carry && allowOverflow) {
            return zeroBuffer(len);
        } else if (carry) {
            throw new IllegalArgumentException("Buffer overflow incrementing " + buffer);
        } else {
            return StaticArrayBuffer.of(next);
        }

    }

    /**
     * Thread safe equals method for StaticBuffer - ByteBuffer equality comparison
     *
     * @param b1
     * @param b2
     * @return
     */
    public static final boolean equals(StaticBuffer b1, ByteBuffer b2) {
        if (b1.length()!=b2.remaining()) return false;
        int p2 = b2.position();
        for (int i=0;i<b1.length();i++) {
            if (b1.getByte(i)!=b2.get(p2+i)) return false;
        }
        return true;
    }


}
