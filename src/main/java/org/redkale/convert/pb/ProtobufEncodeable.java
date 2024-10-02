/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import org.redkale.convert.EnMember;
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

    // 序列化
    default void convertTo(W out, EnMember member, T value) {
        convertTo(out, value);
    }

    // 计算内容长度
    public int computeSize(ProtobufWriter out, int tagSize, T value);

    // 是否需要计算内容长度
    default boolean requireSize() {
        return false;
    }

    // 获取数据类型枚举
    public ProtobufTypeEnum typeEnum();

}
