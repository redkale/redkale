/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert;

import java.util.HashMap;
import java.util.Map;

/**
 * 字段的反序列化集合操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class DeMemberInfo {

    protected final DeMember[] members;

    protected final DeMemberNode memberNode;

    protected final Map<String, DeMember> memberFieldMap;

    protected final Map<Integer, DeMember> memberTagMap;

    protected DeMemberInfo(DeMember... deMembers) {
        this.members = deMembers;
        this.memberFieldMap = new HashMap<>(deMembers.length);
        this.memberTagMap = new HashMap<>(deMembers.length);
        for (DeMember member : deMembers) {
            this.memberFieldMap.put(member.getFieldName(), member);
            this.memberTagMap.put(member.getTag(), member);
        }
        this.memberNode = DeMemberNode.create(deMembers);
    }

    public static DeMemberInfo create(DeMember... deMembers) {
        return new DeMemberInfo(deMembers);
    }

    public int length() {
        return members.length;
    }

    public DeMember[] getMembers() {
        return members;
    }

    public DeMemberNode getMemberNode() {
        return memberNode;
    }

    public DeMember getMemberByTag(int tag) {
        return memberTagMap.get(tag);
    }

    public DeMember getMemberByField(String field) {
        return memberFieldMap.get(field);
    }
}
