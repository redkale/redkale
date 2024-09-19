/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.Reader.ValueType;
import static org.redkale.convert.Reader.ValueType.MAP;
import org.redkale.util.*;

/**
 * 对不明类型的对象进行反序列化。 <br>
 * <b>注意: 目前只支持文本格式</b> <br>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader
 */
public class AnyDecoder<R extends Reader, T> implements Decodeable<R, T> {

    private static final Type collectionObjectType = new TypeToken<Collection<Object>>() {}.getType();

    private static final Type mapObjectType = new TypeToken<Map<String, Object>>() {}.getType();

    private static final Creator<ArrayList> collectionCreator = Creator.create(ArrayList.class);

    private static final Creator<LinkedHashMap> mapCreator = Creator.create(LinkedHashMap.class);

    protected final Decodeable<Reader, ? extends CharSequence> stringDecoder;

    protected final CollectionDecoder collectionDecoder;

    protected final MapDecoder mapDecoder;

    /**
     * 构造函数
     *
     * @param factory ConvertFactory
     */
    public AnyDecoder(final ConvertFactory factory) {
        this(mapCreator, mapObjectType, collectionCreator, collectionObjectType, factory.loadDecoder(String.class));
    }

    protected AnyDecoder(
            Creator<? extends Map> mapCreator,
            Type mapObjectType,
            Creator<? extends Collection> listCreator,
            Type listObjectType,
            Decodeable<Reader, String> keyDecoder) {
        this.stringDecoder = keyDecoder;
        this.collectionDecoder = new CollectionDecoder(listObjectType, Object.class, listCreator, this);
        this.mapDecoder = new MapDecoder(mapObjectType, String.class, Object.class, mapCreator, keyDecoder, this);
    }

    @Override
    public T convertFrom(Reader in) {
        ValueType vt = in.readType();
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

    protected T stringFrom(Reader in) {
        return (T) this.stringDecoder.convertFrom(in);
    }

    @Override
    public Type getType() {
        return void.class;
    }
}
