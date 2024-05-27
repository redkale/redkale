/*
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * 单个对象或对象数组的合并类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 * @param <T> 泛型
 */
public class OneOrList<T> implements java.io.Serializable {

    public static final Type TYPE_OL_STRING = new TypeToken<OneOrList<String>>() {}.getType();

    public static final Type TYPE_OL_INTEGER = new TypeToken<OneOrList<Integer>>() {}.getType();

    public static final Type TYPE_OL_LONG = new TypeToken<OneOrList<Long>>() {}.getType();

    public static final Type TYPE_OL_FLOAT = new TypeToken<OneOrList<Float>>() {}.getType();

    public static final Type TYPE_OL_DOUBLE = new TypeToken<OneOrList<Double>>() {}.getType();

    public static final Type TYPE_OL_BIGINTEGER = new TypeToken<OneOrList<BigInteger>>() {}.getType();

    public static final Type TYPE_OL_BIGDECIMAL = new TypeToken<OneOrList<BigDecimal>>() {}.getType();

    @ConvertColumn(index = 1)
    protected T one;

    @ConvertColumn(index = 2)
    protected List<T> list;

    public OneOrList() {}

    public OneOrList(T one) {
        this.one = one;
    }

    public OneOrList(List<T> list) {
        this.list = list;
    }

    // 序列化
    protected static Encodeable<JsonWriter, OneOrList> createEncoder(final JsonFactory factory, final Type type) {
        Type itemType = parseItemType(type);
        if (itemType == null) {
            return null;
        }
        Encodeable oneEncoder = factory.loadEncoder(itemType);
        Encodeable listEncoder = factory.loadEncoder(TypeToken.createParameterizedType(null, List.class, itemType));
        return new Encodeable<JsonWriter, OneOrList>() {
            @Override
            public void convertTo(JsonWriter out, OneOrList value) {
                if (value == null) {
                    out.writeNull();
                } else if (value.isTypeOne()) {
                    oneEncoder.convertTo(out, value.getOne());
                } else {
                    listEncoder.convertTo(out, value.getList());
                }
            }

            @Override
            public Type getType() {
                return type;
            }
        };
    }

    // 反序列化
    protected static Decodeable<JsonReader, OneOrList> createDecoder(final JsonFactory factory, final Type type) {
        Type itemType = parseItemType(type);
        if (itemType == null) {
            return null;
        }
        Creator<OneOrList> creator =
                Creator.create(type instanceof Class ? (Class) type : (Class) ((ParameterizedType) type).getRawType());
        Decodeable oneDecoder = factory.loadDecoder(itemType);
        Decodeable listDecoder = factory.loadDecoder(TypeToken.createParameterizedType(null, List.class, itemType));
        return new Decodeable<JsonReader, OneOrList>() {
            @Override
            public OneOrList convertFrom(JsonReader in) {
                if (in.isNextArray()) {
                    List val = (List) listDecoder.convertFrom(in);
                    OneOrList rs = creator.create();
                    rs.setList(val);
                    return rs;
                } else {
                    Object val = oneDecoder.convertFrom(in);
                    OneOrList rs = creator.create();
                    rs.setOne(val);
                    return rs;
                }
            }

            @Override
            public Type getType() {
                return type;
            }
        };
    }

    protected static Type parseItemType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            if (pt.getRawType() != OneOrList.class) {
                return null;
            }
            return pt.getActualTypeArguments()[0];
        } else if (type instanceof Class) {
            Class clz = (Class) type;
            while (clz.getSuperclass() != OneOrList.class) {
                clz = clz.getSuperclass();
                if (clz == Object.class) {
                    return null;
                }
            }
            return ((ParameterizedType) clz.getGenericSuperclass()).getActualTypeArguments()[0];
        } else {
            return null;
        }
    }

    @ConvertDisabled
    public boolean isTypeOne() {
        return list == null;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(isTypeOne() ? this.one : this.list);
    }

    public T getOne() {
        return one;
    }

    public void setOne(T one) {
        this.one = one;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
