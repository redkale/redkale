/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
import java.util.Map;
import org.redkale.convert.*;

/**
 * Map的反序列化操作类 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
public class BsonMapDecoder<K, V> extends MapDecoder<BsonReader, K, V> {

    private final boolean skip;

    public BsonMapDecoder(final BsonFactory factory, final Type type, boolean skip) {
        super(factory, type);
        this.skip = skip;
    }

    @Override
    public Map<K, V> convertFrom(BsonReader in) {
        this.checkInited();
        int len = in.readMapB(this.keyDecoder, this.valueDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        Decodeable<BsonReader, K> kdecoder = this.keyDecoder;
        Decodeable<BsonReader, V> vdecoder = this.valueDecoder;
        if (skip) {
            kdecoder = BsonFactory.skipTypeEnum(in.readMapKeyTypeEnum());
            vdecoder = BsonFactory.skipTypeEnum(in.readmapValueTypeEnum());
        }
        final Map<K, V> result = this.creator.create();
        // 固定长度
        for (int i = 0; i < len; i++) {
            K key = kdecoder.convertFrom(in);
            in.readBlank();
            V value = vdecoder.convertFrom(in);
            result.put(key, value);
        }
        in.readMapE();
        return result;
    }
}
