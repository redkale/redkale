/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.protobuf;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 *
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapEncoder<K, V> extends MapEncoder<K, V> {

    private final boolean enumtostring;

    public ProtobufMapEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
    }

    @Override
    protected void writeMemberValue(Writer out, EnMember member, K key, V value, boolean first) {
        ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(out);
        if (member != null) out.writeFieldName(member);
        tmp.writeUInt32(1 << 3 | ProtobufFactory.wireType(keyEncoder.getType(), enumtostring));
        keyEncoder.convertTo(tmp, key);
        tmp.writeUInt32(2 << 3 | ProtobufFactory.wireType(valueEncoder.getType(), enumtostring));
        valueEncoder.convertTo(tmp, value);
        int length = tmp.count();
        ((ProtobufWriter) out).writeUInt32(length);
        ((ProtobufWriter) out).writeTo(tmp.toArray());
    }
}
