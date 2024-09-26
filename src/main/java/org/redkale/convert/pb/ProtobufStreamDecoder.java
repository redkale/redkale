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
 * @param <T> 泛型
 */
public class ProtobufStreamDecoder<T> extends StreamDecoder<ProtobufReader, T> {

    protected final boolean componentSimpled;

    public ProtobufStreamDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    protected ProtobufReader getItemReader(ProtobufReader in, DeMember member, boolean first) {
        return ProtobufFactory.getItemReader(in, member, componentSimpled, first);
    }
}
