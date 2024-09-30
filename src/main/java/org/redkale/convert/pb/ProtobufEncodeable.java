/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import org.redkale.convert.Encodeable;
import org.redkale.convert.Writer;

/**
 * 带tag的反序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer输出的子类
 * @param <T> 序列化的数据类型
 */
public interface ProtobufEncodeable<W extends Writer, T> extends Encodeable<W, T> {
    // 计算内容长度
    public int computeSize(T value);

    // 是否需要计算内容长度
    default boolean requireSize() {
        return false;
    }
}
