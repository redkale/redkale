/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.*;

/**
 * 非基本类型的数组反序列化
 *
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayDecoder<T> extends ArrayDecoder<ProtobufReader, T> {

    protected final boolean componentSimpled;

    public ProtobufArrayDecoder(ProtobufFactory factory, Type type) {
        super(factory, type);
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    public T[] convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        final Decodeable<ProtobufReader, T> itemDecoder = this.componentDecoder;
        in.readArrayB(member, itemDecoder);
        int contentLength = in.readMemberContentLength(member, itemDecoder);
        final List<T> result = new ArrayList();
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
        T[] rs = this.componentArrayFunction.apply(result.size());
        return result.toArray(rs);
    }

    protected ProtobufReader getItemReader(ProtobufReader in, DeMember member, boolean first) {
        return ProtobufFactory.getItemReader(in, member, componentSimpled, first);
    }
}
