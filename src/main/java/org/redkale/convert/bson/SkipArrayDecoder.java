/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
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
public class SkipArrayDecoder<T> extends ArrayDecoder<BsonReader, T> {

    public SkipArrayDecoder(final BsonFactory factory, final Type type) {
        super(factory, type);
    }

    @Override
    protected Decodeable<BsonReader, T> getComponentDecoder(Decodeable<BsonReader, T> decoder, byte[] typevals) {
        if (typevals != null) {
            return BsonFactory.typeEnum(typevals[0]);
        }
        return decoder;
    }
}
