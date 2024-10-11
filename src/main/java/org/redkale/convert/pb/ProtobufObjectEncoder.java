/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.function.BiFunction;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.util.Attribute;
import org.redkale.util.Utility;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufObjectEncoder<T> extends ObjectEncoder<ProtobufWriter, T>
        implements ProtobufEncodeable<ProtobufWriter, T> {

    protected ProtobufObjectEncoder(Type type) {
        super(type);
    }

    @Override
    public final void convertTo(ProtobufWriter out, T value) {
        convertTo(out, null, value);
    }

    @Override
    public void convertTo(ProtobufWriter out, @Nullable EnMember member, T value) {
        this.checkInited();
        if (value == null) {
            out.writeObjectNull(null);
            return;
        }

        ProtobufWriter subout = acceptWriter(out, member, value);
        subout.writeObjectB(value);
        int maxPosition = 0;
        for (EnMember fieldMember : members) {
            maxPosition = fieldMember.getPosition();
            subout.writeObjectField(fieldMember, value);
        }
        if (subout.objExtFunc() != null) {
            ConvertField[] extFields = subout.objExtFunc().apply(value);
            if (extFields != null) {
                Encodeable<ProtobufWriter, ?> anyEncoder = factory.getAnyEncoder();
                for (ConvertField en : extFields) {
                    if (en != null) {
                        maxPosition++;
                        subout.writeObjectField(
                                en.getName(),
                                en.getType(),
                                Math.max(en.getPosition(), maxPosition),
                                anyEncoder,
                                en.getValue());
                    }
                }
            }
        }
        subout.writeObjectE(value);
        offerWriter(out, subout);
    }

    @ClassDepends
    protected ProtobufWriter acceptWriter(ProtobufWriter out, EnMember member, T value) {
        //        if (member != null) {
        //            out.writeLength(computeSize(out, member.getTagSize(), value));
        //            return out;
        //        }
        //        return out;
        return member != null ? out.pollChild() : out;
    }

    @ClassDepends
    protected void offerWriter(ProtobufWriter parent, ProtobufWriter out) {
        if (parent != out) {
            parent.offerChild(out);
        }
    }

    @Override
    protected void initForEachEnMember(ConvertFactory factory, EnMember member) {
        if (member.getIndex() < 1) {
            if (ProtobufFactory.INDEX_CHECK) {
                throw new ConvertException(Utility.orElse(member.getField(), member.getMethod()) + " not found @"
                        + ConvertColumn.class.getSimpleName() + ".index");
            } else {
                member.setPositionToIndex();
            }
        }
        Attribute attr = member.getAttribute();
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        setTag(member, ProtobufFactory.getTag(attr.field(), attr.genericType(), member.getPosition(), enumtostring));
        setTagSize(member, ProtobufFactory.computeSInt32SizeNoTag(member.getTag()));
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, T value) {
        if (value == null) {
            return 0;
        }
        int dataSize = 0;
        BiFunction objFieldFunc = out.objFieldFunc();
        for (EnMember member : members) {
            ProtobufEncodeable encodeable = (ProtobufEncodeable) member.getEncoder();
            Object val = null;
            if (objFieldFunc == null) {
                val = member.getFieldValue(value);
            } else {
                val = objFieldFunc.apply(member.getAttribute(), value);
            }
            if (val != null) {
                int itemTagSize = member.getTagSize();
                int itemDataSize = encodeable.computeSize(out, itemTagSize, val);
                if (itemDataSize > 0) { // 空集合会返回0
                    dataSize += itemTagSize + itemDataSize;
                }
            }
        }
        return dataSize;
    }
}
