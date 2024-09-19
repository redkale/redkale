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
public class ProtobufStreamEncoder<T> extends StreamEncoder<ProtobufWriter, T> {

    protected final boolean simple;

    public ProtobufStreamEncoder(ConvertFactory factory, Type type) {
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
    protected void writeMemberValue(ProtobufWriter out, EnMember member, Object item, boolean first) {
        if (simple) {
            if (item == null) {
                out.writeUInt32(0);
            } else {
                componentEncoder.convertTo(out, item);
            }
            return;
        }
        if (member != null) {
            out.writeFieldName(member);
        }
        if (item instanceof CharSequence) {
            componentEncoder.convertTo(out, item);
        } else {
            ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(out);
            componentEncoder.convertTo(tmp, item);
            out.writeUInt32(tmp.count());
            out.writeTo(tmp.toArray());
        }
    }
}
