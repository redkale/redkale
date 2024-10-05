/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.util;

/**
 * byte树对象, key必须是latin1字符串
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public class ByteTreeNode<T> {

    protected T value;

    protected ByteTreeNode<T>[] nodes = new ByteTreeNode[127];

    protected ByteTreeNode() {}

    public static <T> ByteTreeNode<T> create() {
        return new ByteTreeNode();
    }

    public ByteTreeNode<T> getNode(byte b) {
        return b < 0 ? null : nodes[b];
    }

    public ByteTreeNode<T> getNode(char ch) {
        return ch >= nodes.length ? null : nodes[ch];
    }

    public T getValue() {
        return value;
    }

    public T getValue(String key) {
        ByteTreeNode<T> n = this;
        for (char ch : key.toCharArray()) {
            n = n.nodes[ch];
            if (n == null) {
                return null;
            }
        }
        return n.value;
    }

    protected void put(String key, T value) {
        ByteTreeNode n = this;
        for (char ch : key.toCharArray()) {
            if (ch >= nodes.length) {
                throw new RedkaleException(key + " contains illegal char: " + ch);
            }
            ByteTreeNode s = n.nodes[ch];
            if (s == null) {
                s = new ByteTreeNode();
                n.nodes[ch] = s;
            }
            n = s;
        }
        n.value = value;
    }
}
