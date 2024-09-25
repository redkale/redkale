/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapEncoder<K, V> extends MapEncoder<ProtobufWriter, K, V> {

    private final boolean enumtostring;
    private final int keyTag;
    private final int valTag;

    public ProtobufMapEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
        this.keyTag = 1 << 3 | ProtobufFactory.wireType(keyEncoder.getType(), enumtostring);
        this.valTag = 2 << 3 | ProtobufFactory.wireType(valueEncoder.getType(), enumtostring);
    }

    @Override
    protected void writeMemberValue(ProtobufWriter out, EnMember member, K key, V value, boolean first) {
        ProtobufWriter tmp = out.pollChild();
        if (member != null) {
            out.writeFieldName(member);
        }
        tmp.writeTag(keyTag);
        keyEncoder.convertTo(tmp, key);
        tmp.writeTag(valTag);
        valueEncoder.convertTo(tmp, value);
        out.writeLength(tmp.count());
        out.writeTo(tmp.toArray());
        out.offerChild(tmp);
    }
}
