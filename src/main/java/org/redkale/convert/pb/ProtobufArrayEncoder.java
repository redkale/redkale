/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.annotation.Nonnull;
import org.redkale.convert.*;

/**
 * 非基本类型数组序列化。 注意: 基础类型不能使用此类
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayEncoder<T> extends ArrayEncoder<ProtobufWriter, T>
        implements ProtobufEncodeable<ProtobufWriter, T[]> {

    protected final boolean componentPrimitived;

    protected final boolean componentSimpled;

    public ProtobufArrayEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentPrimitived = getComponentEncoder() instanceof ProtobufPrimitivable;
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
    }

    @Override
    public void convertTo(final ProtobufWriter out, @Nonnull EnMember member, T[] value) {
        this.checkInited();
        if (value == null || value.length == 0) {
            return;
        }
        if (componentPrimitived) {
            convertPrimitivedTo(out, member, value);
        } else {
            convertObjectTo(out, member, value);
        }
    }

    protected void convertObjectTo(final ProtobufWriter out, @Nonnull EnMember member, T[] value) {
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        out.writeArrayB(value.length, itemEncoder, value);
        boolean first = true;
        for (T item : value) {
            if (!first) {
                out.writeField(member);
            }
            if (item == null) {
                out.writeLength(0);
            } else {
                itemEncoder.convertTo(out, member, item);
            }
            first = false;
        }
        out.writeArrayE();
    }

    protected void convertPrimitivedTo(final ProtobufWriter out, @Nonnull EnMember member, T[] value) {
        out.writeLength(computeSize(out, 0, value));
        Encodeable itemCoder = getComponentEncoder();
        for (T item : value) {
            itemCoder.convertTo(out, item);
        }
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, T[] value) {
        if (value == null || value.length == 0) {
            return 0;
        }
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        if (componentPrimitived) {
            int dataSize = 0;
            for (Object item : value) {
                dataSize += itemEncoder.computeSize(out, tagSize, item);
            }
            return dataSize;
        } else {
            int dataSize = tagSize * value.length;
            for (Object item : value) {
                dataSize += itemEncoder.computeSize(out, tagSize, item);
            }
            return ProtobufFactory.computeSInt32SizeNoTag(dataSize) + dataSize;
        }
    }

    @Override
    public boolean requireSize() {
        return !componentPrimitived;
    }

    @Override
    public final ProtobufTypeEnum typeEnum() {
        return ProtobufTypeEnum.BYTES;
    }
}
