/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Collection;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionEncoder<T> extends CollectionEncoder<ProtobufWriter, T> {

    protected final boolean componentSimpled;

    public ProtobufCollectionEncoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentEncoder() instanceof SimpledCoder;
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember member, Collection<T> value) {
        this.checkInited();
        if (value == null || value.isEmpty()) {
            return;
        }
        Encodeable itemEncoder = this.componentEncoder;
        out.writeArrayB(value.size(), itemEncoder, value);
        for (T item : value) {
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
}
