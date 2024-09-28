/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;
import org.redkale.util.Attribute;
import org.redkale.util.Utility;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufObjectEncoder<T> extends ObjectEncoder<ProtobufWriter, T> {

    protected ProtobufObjectEncoder(Type type) {
        super(type);
    }

    @Override
    protected void initForEachEnMember(ConvertFactory factory, EnMember member) {
        if (member.getIndex() < 1) {
            throw new ConvertException(Utility.orElse(member.getField(), member.getMethod()) + " not found @"
                    + ConvertColumn.class.getSimpleName() + ".index");
        }
        Attribute attr = member.getAttribute();
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        setTag(member, ProtobufFactory.getTag(attr.field(), attr.genericType(), member.getPosition(), enumtostring));
    }

    @Override
    protected ProtobufWriter objectWriter(ProtobufWriter out, T value) {
        if (out.length() > out.initOffset) {
            return out.pollChild().configParentFunc(out);
        }
        return out;
    }

    @Override
    protected void offerWriter(ProtobufWriter parent, ProtobufWriter out) {
        if (parent != out) {
            parent.offerChild(out);
        }
    }
}
