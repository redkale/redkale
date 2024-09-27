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
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufCollectionDecoder<T> extends CollectionDecoder<ProtobufReader, T> {

    protected final boolean componentSimpled;

    public ProtobufCollectionDecoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    public Collection<T> convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        final Decodeable<ProtobufReader, T> itemDecoder = this.componentDecoder;
        in.readArrayB(member, itemDecoder);
        int contentLength = in.readMemberContentLength(member, itemDecoder);
        final Collection<T> result = this.creator.create();
        boolean first = true;
        int startPosition = in.position();
        while (in.hasNext(startPosition, contentLength)) {
            ProtobufReader itemReader = getItemReader(in, member, first);
            if (itemReader == null) { // 元素读取完毕
                break;
            }
            result.add(itemDecoder.convertFrom(itemReader));
            first = false;
        }
        in.readArrayE();
        return result;
    }

    protected ProtobufReader getItemReader(ProtobufReader in, DeMember member, boolean first) {
        return ProtobufFactory.getItemReader(in, member, componentSimpled, first);
    }
}
