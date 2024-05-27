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
public class ProtobufArrayDecoder<T> extends ArrayDecoder<T> {

    protected final boolean simple;

    private final boolean string;

    private final boolean enumtostring;

    public ProtobufArrayDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
        Type comtype = this.getComponentType();
        this.string = String.class == comtype;
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
    protected Reader getItemReader(Reader in, DeMember member, boolean first) {
        if (simple) return in;
        return ProtobufFactory.getItemReader(string, simple, in, member, enumtostring, first);
    }
}
