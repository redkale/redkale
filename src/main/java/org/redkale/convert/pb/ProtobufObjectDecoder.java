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
public class ProtobufObjectDecoder<T> extends ObjectDecoder<ProtobufReader, T> {

    protected ProtobufObjectDecoder(Type type) {
        super(type);
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
    protected ProtobufReader objectReader(ProtobufReader in) {
        if (in.position() > in.initoffset) return new ProtobufReader(in.readByteArray());
        return in;
    }

    @Override
    protected boolean hasNext(ProtobufReader in, boolean first) {
        return in.hasNext();
    }

    @Override
    protected Object readDeMemberValue(ProtobufReader in, DeMember member, boolean first) {
        Decodeable decoder = member.getDecoder();
        if (decoder instanceof ProtobufArrayDecoder) {
            return ((ProtobufArrayDecoder) decoder).convertFrom(in, member);
        } else if (decoder instanceof ProtobufCollectionDecoder) {
            return ((ProtobufCollectionDecoder) decoder).convertFrom(in, member);
        } else if (decoder instanceof ProtobufStreamDecoder) {
            return ((ProtobufStreamDecoder) decoder).convertFrom(in, member);
        } else if (decoder instanceof ProtobufMapDecoder) {
            return ((ProtobufMapDecoder) decoder).convertFrom(in, member);
        } else {
            return member.read(in);
        }
    }

    @Override
    protected void readDeMemberValue(ProtobufReader in, DeMember member, T result, boolean first) {
        Decodeable decoder = member.getDecoder();
        if (decoder instanceof ProtobufArrayDecoder) {
            member.getAttribute().set(result, ((ProtobufArrayDecoder) decoder).convertFrom(in, member));
        } else if (decoder instanceof ProtobufCollectionDecoder) {
            member.getAttribute().set(result, ((ProtobufCollectionDecoder) decoder).convertFrom(in, member));
        } else if (decoder instanceof ProtobufStreamDecoder) {
            member.getAttribute().set(result, ((ProtobufStreamDecoder) decoder).convertFrom(in, member));
        } else if (decoder instanceof ProtobufMapDecoder) {
            member.getAttribute().set(result, ((ProtobufMapDecoder) decoder).convertFrom(in, member));
        } else {
            member.read(in, result);
        }
    }
}
