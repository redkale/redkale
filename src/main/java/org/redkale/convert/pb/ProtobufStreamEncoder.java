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
public class ProtobufStreamEncoder<T> extends StreamEncoder<ProtobufWriter, T> {

    protected final boolean componentSimpled;

    public ProtobufStreamEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
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
            out.writeFieldName(member);
            if (item == null) {
                out.writeUInt32(0);
            } else if (componentSimpled) {
                itemEncoder.convertTo(out, item);
            } else {
                ProtobufWriter tmp = out.pollChild();
                itemEncoder.convertTo(tmp, item);
                out.writeLength(tmp.count());
                out.writeTo(tmp.toArray());
                out.offerChild(tmp);
            }
        }
        out.writeArrayE();
    }
}
