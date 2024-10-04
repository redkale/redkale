/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import org.redkale.convert.Decodeable;
import org.redkale.convert.Encodeable;

/**
 * 只能用于基本类型， 不能用于如String的其他类型
 * @author zhangjx
 * @param <T> 基本类型泛型
 */
public interface ProtobufPrimitivable<T> extends Decodeable<ProtobufReader, T>, Encodeable<ProtobufWriter, T> {

    // 获取java类型分类
    public Class primitiveType();

    // 计算内容长度
    public int computeSize(T value);
}
