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

    protected final boolean componentSimpled;
    protected final boolean componentSizeRequired;

    public ProtobufArrayEncoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
        this.componentSizeRequired = !(getComponentEncoder() instanceof ProtobufPrimitivable);
    }

    @Override
    public void convertTo(final ProtobufWriter out, @Nonnull EnMember member, T[] value) {
        this.checkInited();
        if (value == null || value.length < 1) {
            return;
        }
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

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, T[] value) {
        if (value == null || value.length < 1) {
            return 0;
        }
        int dataSize = 0;
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        for (T item : value) {
            dataSize += itemEncoder.computeSize(out, tagSize, item);
        }
        if (componentSizeRequired) {
            dataSize += tagSize * value.length;
        }
        return ProtobufFactory.computeSInt32SizeNoTag(dataSize) + dataSize;
    }

    @Override
    public boolean requireSize() {
        return !componentSimpled;
    }

    @Override
    public final ProtobufTypeEnum typeEnum() {
        return ProtobufTypeEnum.BYTES;
    }
}
