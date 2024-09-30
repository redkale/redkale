/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
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
    public void convertTo(ProtobufWriter out, EnMember member, T[] value) {
        this.checkInited();
        if (value == null || value.length < 1) {
            return;
        }
        Encodeable itemEncoder = this.componentEncoder;
        T[] array = value;
        out.writeArrayB(array.length, itemEncoder, array);
        for (T item : array) {
            out.writeField(member);
            if (item == null) {
                out.writeUInt32(0);
            } else if (componentSimpled) {
                itemEncoder.convertTo(out, item);
            } else {
                ProtobufWriter tmp = out.pollChild();
                itemEncoder.convertTo(tmp, item);
                out.writeTuple(tmp);
                out.offerChild(tmp);
            }
        }
        out.writeArrayE();
    }

    @Override
    public int computeSize(T[] value) {
        if (value == null || value.length < 1) {
            return 0;
        }
        int size = 0;
        ProtobufEncodeable itemEncoder = (ProtobufEncodeable) this.componentEncoder;
        for (T item : value) {
            size += itemEncoder.computeSize(item);
        }
        return size;
    }

    @Override
    public boolean requireSize() {
        return componentSizeRequired;
    }
}
