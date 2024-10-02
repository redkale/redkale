/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
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

    protected boolean memberSizeRequired;

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

        ProtobufWriter subout = acceptWriter(out, member);
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
    protected ProtobufWriter acceptWriter(ProtobufWriter out, EnMember member) {
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
            throw new ConvertException(Utility.orElse(member.getField(), member.getMethod()) + " not found @"
                    + ConvertColumn.class.getSimpleName() + ".index");
        }
        Attribute attr = member.getAttribute();
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        this.memberSizeRequired |= !(member.getEncoder() instanceof SimpledCoder)
                && ((ProtobufEncodeable) member.getEncoder()).requireSize();
        setTag(member, ProtobufFactory.getTag(attr.field(), attr.genericType(), member.getPosition(), enumtostring));
        setTagSize(member, ProtobufFactory.computeSInt32SizeNoTag(member.getTag()));
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, T value) {
        int size = 0;
        for (EnMember member : members) {
            ProtobufEncodeable encodeable = (ProtobufEncodeable) member.getEncoder();
            int itemTagSize = member.getTagSize();
            int itemDataSize = encodeable.computeSize(out, itemTagSize, member.getFieldValue(value));
            if (itemDataSize > 0) {
                size += itemTagSize + itemDataSize;
            }
        }
        return size;
    }

    public boolean requiredMemberSize() {
        return true||memberSizeRequired;
    }

    @Override
    public final boolean requireSize() {
        return true;
    }

    @Override
    public final ProtobufTypeEnum typeEnum() {
        return ProtobufTypeEnum.BYTES;
    }
}
