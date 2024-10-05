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

    protected final byte index;

    protected final ByteTreeNode<T> parent;

    protected T value;

    protected ByteTreeNode<T>[] nodes = new ByteTreeNode[127];

    protected ByteTreeNode() {
        this(null, 0);
    }

    protected ByteTreeNode(ByteTreeNode<T> parent, int index) {
        this.parent = parent;
        if (index < 0 || index >= nodes.length) {
            throw new RedkaleException(index + " is illegal");
        }
        this.index = (byte) index;
    }

    public static <T> ByteTreeNode<T> create() {
        return new ByteTreeNode(null, 0);
    }

    public ByteTreeNode<T> getNode(byte b) {
        return b < 0 ? null : nodes[b];
    }

    public ByteTreeNode<T> getNode(char ch) {
        return ch >= nodes.length ? null : nodes[ch];
    }

    public ByteTreeNode<T> getParent() {
        return parent;
    }

    public byte getIndex() {
        return index;
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

    protected ByteTreeNode<T> put(String key, T value) {
        ByteTreeNode<T> n = this;
        int i = 0;
        for (char ch : key.toCharArray()) {
            if (ch >= nodes.length) {
                throw new RedkaleException(key + " contains illegal char: " + ch);
            }
            i++;
            ByteTreeNode<T> s = n.nodes[ch];
            if (s == null) {
                s = createNode(n, ch, key, i);
                n.nodes[ch] = s;
            }
            n = s;
        }
        n.value = value;
        return n;
    }

    protected ByteTreeNode<T> createNode(ByteTreeNode<T> parent, int index, String key, int subLen) {
        return new ByteTreeNode(parent, index);
    }

    @Override
    public String toString() {
        return "ByteTreeNode{" + "index='" + (char) index + "', value=" + value + '}';
    }
}
