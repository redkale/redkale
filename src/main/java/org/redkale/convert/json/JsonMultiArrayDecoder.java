/*
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.*;

/**
 * 数组数据中包含不同Type的反序列化解析器 <br>
 * 如: ['aaa',{'name':'hahah'}], 需要两个Type来反序列化(String, Map&#60;String, String&#62;) <br>
 * <b>注意: type的个数必须大于或等于结果数组元素个数， 此解析器对象不会被缓存，每次都会创建新实例</b>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class JsonMultiArrayDecoder implements Decodeable<JsonReader, Object[]> {

    protected final JsonFactory factory;

    protected final Type[] types;

    protected final Decodeable[] decoders;

    public JsonMultiArrayDecoder(final JsonFactory factory, final Type[] types) {
        this.factory = factory;
        this.types = types;
        this.decoders = new Decodeable[types.length];
        for (int i = 0; i < types.length; i++) {
            this.decoders[i] = factory.loadDecoder(types[i]);
        }
    }

    @Override
    public Object[] convertFrom(JsonReader in) {
        return convertFrom(in, null);
    }

    public Object[] convertFrom(JsonReader in, DeMember member) {
        int len = in.readArrayB(member, null);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        // len must be Reader.SIGN_VARIABLE
        final List<Object> result = new ArrayList();
        int index = -1;
        final Decodeable[] coders = this.decoders;
        while (in.hasNext()) {
            result.add(coders[++index % coders.length].convertFrom(in));
        }
        in.readArrayE();
        return result.toArray(new Object[result.size()]);
    }

    @Override
    public Type getType() {
        return Object[].class;
    }
}
