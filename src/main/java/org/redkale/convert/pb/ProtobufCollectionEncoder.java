/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Collection;
import org.redkale.annotation.Nonnull;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionEncoder<T> extends CollectionEncoder<ProtobufWriter, T>
        implements ProtobufEncodeable<ProtobufWriter, Collection<T>> {

    protected final boolean componentPrimitived;

    protected final boolean componentSimpled;

    public ProtobufCollectionEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentPrimitived = getComponentEncoder() instanceof ProtobufPrimitivable;
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
    }

    @Override
    public void convertTo(final ProtobufWriter out, @Nonnull EnMember member, Collection<T> value) {
        this.checkInited();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (componentPrimitived) {
            convertPrimitivedTo(out, member, value);
        } else {
            convertObjectTo(out, member, value);
        }
    }

    protected void convertObjectTo(final ProtobufWriter out, @Nonnull EnMember member, Collection<T> value) {
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        out.writeArrayB(value.size(), itemEncoder, value);
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

    protected void convertPrimitivedTo(final ProtobufWriter out, @Nonnull EnMember member, Collection<T> value) {
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        int dataSize = 0;
        for (Object item : value) {
            dataSize += itemEncoder.computeSize(out, 0, item);
        }
        out.writeLength(dataSize);
        for (T item : value) {
            itemEncoder.convertTo(out, item);
        }
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, Collection<T> value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        if (componentPrimitived) {
            int dataSize = 0;
            for (T item : value) {
                dataSize += itemEncoder.computeSize(out, tagSize, item);
            }
            return ProtobufFactory.computeSInt32SizeNoTag(dataSize) + dataSize;
        } else {
            int dataSize = tagSize * (value.size() - 1);
            for (T item : value) {
                dataSize += itemEncoder.computeSize(out, tagSize, item);
            }
            return dataSize;
        }
    }
}
