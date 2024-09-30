/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
import java.util.Collection;
import org.redkale.convert.*;

/**
 * Collection的反序列化操作类 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 反解析的集合元素类型
 */
public class BsonCollectionDecoder<T> extends CollectionDecoder<BsonReader, T> {

    private final boolean skip;

    public BsonCollectionDecoder(final ConvertFactory factory, final Type type, boolean skip) {
        super(factory, type);
        this.skip = skip;
    }

    @Override
    public Collection<T> convertFrom(BsonReader in) {
        this.checkInited();
        int len = in.readArrayB(componentDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        Decodeable<BsonReader, T> itemDecoder = this.componentDecoder;
        if (skip) {
            itemDecoder = BsonFactory.skipTypeEnum(in.readArrayItemTypeEnum());
        }
        final Collection<T> result = this.creator.create();
        // 固定长度
        for (int i = 0; i < len; i++) {
            result.add(itemDecoder.convertFrom(in));
        }
        in.readArrayE();
        return result;
    }
}
