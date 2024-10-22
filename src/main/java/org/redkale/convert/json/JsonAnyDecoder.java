/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.redkale.convert.CollectionDecoder;
import org.redkale.convert.ConvertFactory;
import org.redkale.convert.Decodeable;
import org.redkale.convert.MapDecoder;
import org.redkale.util.Creator;
import org.redkale.util.TypeToken;

/**
 * 对不明类型的对象进行反序列化。 <br>
 * <b>注意: 目前只支持文本格式</b> <br>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 */
public class JsonAnyDecoder<T> implements Decodeable<JsonReader, T> {

    private static final Type collectionObjectType = new TypeToken<Collection<Object>>() {}.getType();

    private static final Type mapObjectType = new TypeToken<Map<String, Object>>() {}.getType();

    private static final Creator<ArrayList> collectionCreator = Creator.create(ArrayList.class);

    private static final Creator<LinkedHashMap> mapCreator = Creator.create(LinkedHashMap.class);

    protected final Decodeable<JsonReader, ? extends CharSequence> stringDecoder;

    protected final CollectionDecoder collectionDecoder;

    protected final MapDecoder mapDecoder;

    /**
     * 构造函数
     *
     * @param factory ConvertFactory
     */
    public JsonAnyDecoder(final ConvertFactory factory) {
        this(mapCreator, mapObjectType, collectionCreator, collectionObjectType, factory.loadDecoder(String.class));
    }

    protected JsonAnyDecoder(
            Creator<? extends Map> mapCreator,
            Type mapObjectType,
            Creator<? extends Collection> listCreator,
            Type listObjectType,
            Decodeable<JsonReader, String> keyDecoder) {
        this.stringDecoder = keyDecoder;
        this.collectionDecoder = new CollectionDecoder(listObjectType, Object.class, listCreator, this);
        this.mapDecoder = new MapDecoder(mapObjectType, String.class, Object.class, mapCreator, keyDecoder, this);
    }

    @Override
    public T convertFrom(JsonReader in) {
        JsonReader.ValueType vt = in.readType();
        if (vt == null) {
            return null;
        }
        switch (vt) {
            case ARRAY:
                return (T) this.collectionDecoder.convertFrom(in);
            case MAP:
                return (T) this.mapDecoder.convertFrom(in);
        }
        return (T) stringFrom(in);
    }

    protected T stringFrom(JsonReader in) {
        return (T) this.stringDecoder.convertFrom(in);
    }

    @Override
    public Type getType() {
        return Object.class;
    }
}
