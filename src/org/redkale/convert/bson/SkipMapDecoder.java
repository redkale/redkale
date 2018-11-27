/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * Map的反序列化操作类 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
public class SkipMapDecoder<K, V> extends MapDecoder<K, V> {

    public SkipMapDecoder(final ConvertFactory factory, final Type type) {
        super(factory, type);
    }

    @Override
    protected Decodeable<Reader, K> getKeyDecoder(Decodeable<Reader, K> decoder, byte[] typevals) {
        if (typevals != null) return BsonFactory.typeEnum(typevals[0]);
        return decoder;
    }

    @Override
    protected Decodeable<Reader, V> getValueDecoder(Decodeable<Reader, V> decoder, byte[] typevals) {
        if (typevals != null) return BsonFactory.typeEnum(typevals[1]);
        return decoder;
    }
}
