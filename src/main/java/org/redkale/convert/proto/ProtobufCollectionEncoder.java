/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.proto;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.*;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionEncoder<T> extends CollectionEncoder<T> {

    protected final boolean simple;

    public ProtobufCollectionEncoder(ConvertFactory factory, Type type) {
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
    protected void writeMemberValue(Writer out, EnMember member, Object item, boolean first) {
        if (simple) {
            if (item == null) {
                ((ProtobufWriter) out).writeUInt32(0);
            } else {
                componentEncoder.convertTo(out, item);
            }
            return;
        }
        if (member != null) out.writeFieldName(member);
        if (item == null) {
            ((ProtobufWriter) out).writeUInt32(0);
        } else if (item instanceof CharSequence) {
            componentEncoder.convertTo(out, item);
        } else {
            ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(out);
            componentEncoder.convertTo(tmp, item);
            int length = tmp.count();
            ((ProtobufWriter) out).writeUInt32(length);
            ((ProtobufWriter) out).writeTo(tmp.toArray());
        }
    }
}
