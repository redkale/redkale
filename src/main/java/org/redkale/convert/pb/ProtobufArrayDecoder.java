/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * 非基本类型的数组反序列化
 *
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayDecoder<T> extends ArrayDecoder<ProtobufReader, T> {

    protected final boolean componentSimpled;

    public ProtobufArrayDecoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    protected ProtobufReader getItemReader(ProtobufReader in, DeMember member, boolean first) {
        return ProtobufFactory.getItemReader(in, member, componentSimpled, first);
    }
}
