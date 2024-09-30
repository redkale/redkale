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

    private final boolean skip;

    public BsonArrayDecoder(final BsonFactory factory, final Type type, boolean skip) {
        super(factory, type);
        this.skip = skip;
    }

    @Override
    public T[] convertFrom(BsonReader in) {
        this.checkInited();
        int len = in.readArrayB(this.componentDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        Decodeable<BsonReader, T> itemDecoder = this.componentDecoder;
        if (skip) {
            itemDecoder = BsonFactory.skipTypeEnum(in.readArrayItemTypeEnum());
        }
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
