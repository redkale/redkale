/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.*;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayEncoder<T> extends ArrayEncoder<ProtobufWriter, T> {

    protected final boolean simple;

    public ProtobufArrayEncoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        Type comtype = this.getComponentType();
        this.simple = Boolean.class == comtype
                || Short.class == comtype
                || Character.class == comtype
                || Integer.class == comtype
                || Float.class == comtype
                || Long.class == comtype
                || Double.class == comtype
                || AtomicInteger.class == comtype
                || AtomicLong.class == comtype;
    }

    @Override
    protected void writeMemberValue(ProtobufWriter out, EnMember member, Encodeable encoder, Object item, int index) {
        if (simple) {
            if (item == null) {
                out.writeUInt32(0);
            } else {
                componentEncoder.convertTo(out, item);
            }
            return;
        }
        if (member != null) out.writeFieldName(member);
        if (item == null) {
            out.writeUInt32(0);
        } else if (item instanceof CharSequence) {
            encoder.convertTo(out, item);
        } else {
            ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(out);
            encoder.convertTo(tmp, item);
            out.writeLength(tmp.count());
            out.writeTo(tmp.toArray());
        }
    }
}
