/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.stream.Stream;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufStreamEncoder<T> extends StreamEncoder<ProtobufWriter, T>
        implements ProtobufEncodeable<ProtobufWriter, Stream<T>> {

    protected final boolean componentSimpled;
    protected final boolean componentSizeRequired;

    public ProtobufStreamEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
        this.componentSizeRequired = !(getComponentEncoder() instanceof ProtobufPrimitivable);
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember member, Stream<T> value) {
        this.checkInited();
        Object[] array = value == null ? null : value.toArray();
        if (array == null || array.length < 1) {
            return;
        }
        Encodeable itemEncoder = this.componentEncoder;
        out.writeArrayB(array.length, itemEncoder, array);
        for (Object item : array) {
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
    public int computeSize(Stream<T> value) {
        return 0;
    }

    @Override
    public boolean requireSize() {
        return componentSizeRequired;
    }
}
