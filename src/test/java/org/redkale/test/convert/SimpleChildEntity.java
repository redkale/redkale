/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.net.*;
import java.util.*;

/** @author zhangjx */
public class SimpleChildEntity extends SimpleEntity {

    private short st = -1234;

    private String extend;

    public static SimpleChildEntity create() {
        SimpleChildEntity v = new SimpleChildEntity();
        v.setName("this is name\n \"test");
        v.setId(1000000001);
        v.setAddrs(new int[] {22222, 33333, 44444, 55555, 66666, 77777, 88888, 99999});
        v.setStrings(new String[] {"zzz", "yyy", "xxx"});
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
        v.setAddr(new InetSocketAddress("127.0.0.1", 6666));
        return v;
    }

    public short getSt() {
        return st;
    }

    public void setSt(short st) {
        this.st = st;
    }

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }
}
