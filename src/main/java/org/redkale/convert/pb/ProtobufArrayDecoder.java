/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayDecoder<T> extends ArrayDecoder<ProtobufReader, T> {

    protected final boolean simple;

    private final boolean string;

    private final boolean enumtostring;

    public ProtobufArrayDecoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = factory.enumtostring;
        Type comtype = this.getComponentType();
        this.string = String.class == comtype;
        this.simple = ProtobufFactory.isNoLenBytesType(comtype);
    }

    @Override
    protected ProtobufReader getItemReader(ProtobufReader in, DeMember member, boolean first) {
        if (simple) return in;
        return ProtobufFactory.getItemReader(string, simple, in, member, enumtostring, first);
    }
}
