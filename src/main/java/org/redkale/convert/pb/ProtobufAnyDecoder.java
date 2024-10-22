/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.Decodeable;

/**
 *
 * @author zhangjx
 */
public class ProtobufAnyDecoder<T> implements Decodeable<ProtobufReader, T> {

    final ProtobufFactory factory;

    ProtobufAnyDecoder(ProtobufFactory factory) {
        this.factory = factory;
    }

    @Override
    public T convertFrom(ProtobufReader in) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Type getType() {
        return Object.class;
    }
}
