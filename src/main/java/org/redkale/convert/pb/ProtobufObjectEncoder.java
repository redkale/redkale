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

/** @author zhangjx */
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
        setTag(
                member,
                ProtobufFactory.getTag(
                        attr.field(),
                        attr.genericType(),
                        member.getPosition(),
                        ((ProtobufFactory) factory).enumtostring));
    }

    @Override
    protected ProtobufWriter objectWriter(ProtobufWriter out, T value) {
        if (out.count() > out.initOffset) {
            return new ProtobufWriter(out, out.getFeatures()).configFieldFunc(out);
        }
        return out;
    }
}
