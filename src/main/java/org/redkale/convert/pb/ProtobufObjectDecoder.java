/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufObjectDecoder<T> extends ObjectDecoder<ProtobufReader, T>
        implements TagDecodeable<ProtobufReader, T> {

    protected ProtobufObjectDecoder(Type type) {
        super(type);
    }

    @Override
    public T convertFrom(ProtobufReader in, DeMember member) {
        if (member == null) {
            return super.convertFrom(in);
        } else {
            final int limit = in.limit();
            int contentLen = in.readRawVarint32();
            in.limit(in.position() + contentLen + 1);
            T result = super.convertFrom(in);
            in.limit(limit);
            return result;
        }
    }

    @Override
    protected void initForEachDeMember(ConvertFactory factory, DeMember member) {
        if (member.getIndex() < 1) {
            throw new ConvertException(Utility.orElse(member.getField(), member.getMethod()) + " not found @"
                    + ConvertColumn.class.getSimpleName() + ".index");
        }
        Attribute attr = member.getAttribute();
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        setTag(member, ProtobufFactory.getTag(attr.field(), attr.genericType(), member.getPosition(), enumtostring));
    }

    @Override
    protected Object readDeMemberValue(ProtobufReader in, DeMember member, boolean first) {
        Decodeable decoder = member.getDecoder();
        if (decoder instanceof TagDecodeable) {
            return member.readByTag(in);
        } else {
            return member.read(in);
        }
    }

    @Override
    protected void readDeMemberValue(ProtobufReader in, DeMember member, T result, boolean first) {
        Decodeable decoder = member.getDecoder();
        if (decoder instanceof TagDecodeable) {
            member.readByTag(in, result);
        } else {
            member.read(in, result);
        }
    }

}
