/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Collection;
import org.redkale.convert.*;
import org.redkale.util.Utility;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionEncoder<T> extends CollectionEncoder<ProtobufWriter, T>
        implements ProtobufEncodeable<ProtobufWriter, Collection<T>> {

    protected final boolean componentSimpled;
    protected final boolean componentSizeRequired;

    public ProtobufCollectionEncoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
        this.componentSizeRequired = !(getComponentEncoder() instanceof ProtobufPrimitivable);
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember member, Collection<T> value) {
        this.checkInited();
        if (Utility.isEmpty(value)) {
            return;
        }
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        out.writeArrayB(value.size(), itemEncoder, value);
        for (T item : value) {
            out.writeField(member);
            if (item == null) {
                out.writeLength(0);
            } else if (componentSimpled) {
                itemEncoder.convertTo(out, member, item);
            } else {
                ProtobufWriter tmp = out.pollChild();
                itemEncoder.convertTo(tmp, member, item);
                out.offerChild(tmp);
            }
        }
        out.writeArrayE();
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, Collection<T> value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int dataSize = 0;
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        for (T item : value) {
            dataSize += itemEncoder.computeSize(out, tagSize, item);
        }
        if (componentSizeRequired) {
            dataSize += tagSize * value.size();
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
