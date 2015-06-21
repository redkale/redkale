/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import java.util.*;

/**
 *
 * @author zhangjx
 */
public class TestEntry2 extends TestEntry {

    private String extend;

    public static TestEntry2 create() {
        TestEntry2 v = new TestEntry2();
        v.setName("this is name\n \"test");
        v.setId(1000000001);
        v.setAddrs(new int[]{22222, 33333, 44444, 55555, 66666, 77777, 88888, 99999});
        List<String> list = new ArrayList<>();
        list.add("aaaa");
        list.add("bbbb");
        list.add("cccc");
        v.setLists(list);
        Map<String, Integer> map = new HashMap<>();
        map.put("AAA", 111);
        map.put("BBB", 222);
        map.put("CCC", 333);
        v.setMap(map);
        v.setExtend("hahaha");
        return v;
    }

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }

}
