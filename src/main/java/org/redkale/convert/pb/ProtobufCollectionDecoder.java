/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.Collection;
import org.redkale.convert.*;

/**
 * 非基本类型的数组反序列化
 *
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionDecoder<T> extends CollectionDecoder<ProtobufReader, T>
        implements ProtobufTagDecodeable<ProtobufReader, Collection<T>> {

    protected final boolean componentSimpled;

    public ProtobufCollectionDecoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    public Collection<T> convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        final boolean simpled = this.componentSimpled;
        final Decodeable<ProtobufReader, T> itemDecoder = this.componentDecoder;
        in.readArrayB(itemDecoder);
        final Collection<T> result = this.creator.create();
        final int limit = in.limit();
        while (in.hasNext()) {
            boolean nodata = false;
            if (!simpled) {
                int contentLen = in.readRawVarint32();
                if (contentLen == 0) {
                    nodata = true;
                } else {
                    in.limit(in.position() + contentLen + 1);
                }
            }
            if (nodata) {
                result.add(null);
            } else {
                result.add(itemDecoder.convertFrom(in));
                in.limit(limit);
            }
            if (!in.readNextTag(member)) { // 元素结束
                break;
            }
        }
        in.readArrayE();
        return result;
    }
}
