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

    public BsonCollectionDecoder(final ConvertFactory factory, final Type type) {
        super(factory, type);
    }

    @Override
    public Collection<T> convertFrom(BsonReader in, DeMember member) {
        this.checkInited();
        byte[] typevals = new byte[1];
        int len = in.readArrayB(member, typevals, componentDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        final Decodeable<BsonReader, T> itemDecoder = BsonFactory.typeEnum(typevals[0]);
        final Collection<T> result = this.creator.create();
        // 固定长度
        for (int i = 0; i < len; i++) {
            result.add(itemDecoder.convertFrom(in));
        }
        in.readArrayE();
        return result;
    }
}
