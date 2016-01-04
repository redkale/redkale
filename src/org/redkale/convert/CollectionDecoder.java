/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Creator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * 对象集合的反序列化.
 * 集合大小不能超过 32767。  在BSON中集合大小设定的是short，对于大于32767长度的集合传输会影响性能，所以没有采用int存储。
 * 支持一定程度的泛型。
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class CollectionDecoder<T> implements Decodeable<Reader, Collection<T>> {

    private final Type type;

    private final Type componentType;

    protected Creator<Collection<T>> creator;

    private final Decodeable<Reader, T> decoder;

    public CollectionDecoder(final Factory factory, final Type type) {
        this.type = type;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            this.componentType = pt.getActualTypeArguments()[0];
            this.creator = factory.loadCreator((Class) pt.getRawType());
            factory.register(type, this);
            this.decoder = factory.loadDecoder(this.componentType);
        } else {
            throw new ConvertException("collectiondecoder not support the type (" + type + ")");
        }
    }

    @Override
    public Collection<T> convertFrom(Reader in) {
        final int len = in.readArrayB();
        if (len == Reader.SIGN_NULL) return null;
        final Decodeable<Reader, T> localdecoder = this.decoder;
        final Collection<T> result = this.creator.create();
        if (len == Reader.SIGN_NOLENGTH) {
            while (in.hasNext()) {
                result.add(localdecoder.convertFrom(in));
            }
        } else {
            for (int i = 0; i < len; i++) {
                result.add(localdecoder.convertFrom(in));
            }
        }
        in.readArrayE();
        return result;
    }

    @Override
    public Type getType() {
        return type;
    }

}
