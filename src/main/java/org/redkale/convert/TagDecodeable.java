/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert;

/**
 * 带tag的反序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <T> 反解析的数据类型
 */
public interface TagDecodeable<R extends Reader, T> extends Decodeable<R, T> {
    /**
     * 反序列化操作
     *
     * @param in R
     * @param member DeMember
     * @return T
     */
    public T convertFrom(final R in, DeMember member);
}
