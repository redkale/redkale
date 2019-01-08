/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.util.*;
import org.redkale.convert.Reader.ValueType;
import static org.redkale.convert.Reader.ValueType.MAP;

/**
 * 对不明类型的对象进行反序列化。 <br>
 * <b>注意: 目前只支持文本格式</b> <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class AnyDecoder implements Decodeable<Reader, Object> {

    private static final Type collectionObjectType = new TypeToken<Collection<Object>>() {
    }.getType();

    private static final Type mapObjectType = new TypeToken<Map<String, Object>>() {
    }.getType();

    private static final Creator<ArrayList> collectionCreator = Creator.create(ArrayList.class);

    private static final Creator<HashMap> mapCreator = Creator.create(HashMap.class);

    protected final Decodeable<Reader, String> stringDecoder;

    protected final CollectionDecoder collectionDecoder;

    protected final MapDecoder mapDecoder;

    public AnyDecoder(final ConvertFactory factory) {
        this.stringDecoder = factory.loadDecoder(String.class);
        this.collectionDecoder = new CollectionDecoder(factory, collectionObjectType, Object.class, collectionCreator, this);
        this.mapDecoder = new MapDecoder(factory, mapObjectType, String.class, Object.class, mapCreator, stringDecoder, this);
    }

    @Override
    public Object convertFrom(Reader in) {
        ValueType vt = in.readType();
        if (vt == null) return null;
        switch (vt) {
            case ARRAY:
                return this.collectionDecoder.convertFrom(in);
            case MAP:
                return this.mapDecoder.convertFrom(in);
        }
        return this.stringDecoder.convertFrom(in);
    }

    @Override
    public Type getType() {
        return void.class;
    }

}
