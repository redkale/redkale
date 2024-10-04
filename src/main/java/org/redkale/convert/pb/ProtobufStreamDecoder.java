/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <T> 泛型
 */
public class ProtobufStreamDecoder<T> extends StreamDecoder<ProtobufReader, T>
        implements ProtobufTagDecodeable<ProtobufReader, Stream<T>> {

    protected final boolean componentPrimitived;

    protected final boolean componentSimpled;

    public ProtobufStreamDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.componentPrimitived = getComponentDecoder() instanceof ProtobufPrimitivable;
        this.componentSimpled = getComponentDecoder() instanceof SimpledCoder;
    }

    @Override
    public Stream<T> convertFrom(ProtobufReader in, DeMember member) {
        this.checkInited();
        if (componentPrimitived) {
            return convertPrimitivedFrom(in, member);
        } else if (componentSimpled) {
            return convertSimpledFrom(in, member);
        } else {
            return convertObjectFrom(in, member);
        }
    }

    protected Stream<T> convertObjectFrom(ProtobufReader in, DeMember member) {
        final Decodeable<ProtobufReader, T> itemDecoder = this.componentDecoder;
        in.readArrayB(itemDecoder);
        final List<T> result = new ArrayList();
        final int limit = in.limit();
        while (in.hasNext()) {
            // 读长度
            int contentLen = in.readRawVarint32();
            // 读数据
            if (contentLen == 0) {
                result.add(null);
            } else {
                in.limit(in.position() + contentLen + 1);
                result.add(itemDecoder.convertFrom(in));
                in.limit(limit);
            }
            if (!in.readNextTag(member)) { // 元素结束
                break;
            }
        }
        in.readArrayE();
        return result.stream();
    }

    protected Stream<T> convertSimpledFrom(ProtobufReader in, DeMember member) {
        final Decodeable<ProtobufReader, T> itemDecoder = this.componentDecoder;
        in.readArrayB(itemDecoder);
        final List<T> result = new ArrayList();
        while (in.hasNext()) {
            // 读数据
            result.add(itemDecoder.convertFrom(in));
            if (!in.readNextTag(member)) { // 元素结束
                break;
            }
        }
        in.readArrayE();
        return result.stream();
    }

    protected Stream<T> convertPrimitivedFrom(ProtobufReader in, DeMember member) {
        ProtobufPrimitivable<T> primCoder = (ProtobufPrimitivable) this.componentDecoder;
        List<T> result = new ArrayList<>();
        int len = in.readRawVarint32();
        while (len > 0) {
            T val = primCoder.convertFrom(in);
            len -= primCoder.computeSize(val);
            result.add(val);
        }
        return result.stream();
    }
}
