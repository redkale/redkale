/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.*;

/**
 * 数组的反序列化操作类 <br>
 * 对象数组的反序列化，不包含int[]、long[]这样的primitive class数组。 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 反解析的数组元素类型
 */
public class BsonArrayDecoder<T> extends ArrayDecoder<BsonReader, T> {

    public BsonArrayDecoder(final BsonFactory factory, final Type type) {
        super(factory, type);
    }

    @Override
    public T[] convertFrom(BsonReader in, DeMember member) {
        this.checkInited();
        byte[] typevals = new byte[1];
        int len = in.readArrayB(member, typevals, this.componentDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        final Decodeable<BsonReader, T> itemDecoder = BsonFactory.typeEnum(typevals[0]);
        final List<T> result = new ArrayList();
        // 固定长度
        for (int i = 0; i < len; i++) {
            result.add(itemDecoder.convertFrom(in));
        }
        in.readArrayE();
        T[] rs = this.componentArrayFunction.apply(result.size());
        return result.toArray(rs);
    }
}
