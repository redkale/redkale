/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert;

/**
 * 字段的反序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class DeMemberNode {

    protected static final int MIN_INDEX = 32;

    protected DeMember value;

    protected DeMemberNode[] nodes = new DeMemberNode[127 - MIN_INDEX];

    public DeMemberNode getNode(char ch) {
        if (ch <= MIN_INDEX || ch > 127) {
            return null;
        }
        return nodes[ch - MIN_INDEX];
    }

    public DeMember getMember(String field) {
        char[] chs = field.toCharArray();
        DeMemberNode n = this;
        for (int i = 0; i < chs.length; i++) {
            n = n.nodes[chs[i] - MIN_INDEX];
            if (n == null) {
                break;
            }
        }
        return n == null ? null : n.value;
    }

    public DeMember getValue() {
        return value;
    }

    private void add(DeMember member) {
        char[] chs = member.attribute.field().toCharArray();
        DeMemberNode n = this;
        for (int i = 0; i < chs.length; i++) {
            DeMemberNode s = n.nodes[chs[i] - MIN_INDEX];
            if (s == null) {
                s = new DeMemberNode();
                n.nodes[chs[i] - MIN_INDEX] = s;
            }
            n = s;
        }
        n.value = member;
    }

    public static DeMemberNode create(DeMember... members) {
        DeMemberNode root = new DeMemberNode();
        for (DeMember member : members) {
            root.add(member);
        }
        return root;
    }
}
