/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.net.*;
import java.util.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.*;
import org.redkale.util.Creator;

/** @author zhangjx */
public class SimpleEntity {

    @ConvertColumn(index = 7)
    private String name;

    @ConvertColumn(index = 3)
    private String desc = "";

    @ConvertColumn(index = 4)
    private int id = (int) System.currentTimeMillis();

    @ConvertColumn(index = 2)
    private int[] addrs;

    @ConvertColumn(index = 5)
    private List<String> lists;

    @ConvertColumn(index = 8)
    private String[] strings;

    @ConvertColumn(index = 6)
    private Map<String, Integer> map;

    @ConvertColumn(index = 1)
    private InetSocketAddress addr;

    public static SimpleEntity create() {
        SimpleEntity v = new SimpleEntity();
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
        v.setAddr(new InetSocketAddress("127.0.0.1", 6666));
        return v;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(JsonConvert.root().convertTo(create()));
        Creator<SimpleEntity> creator = Creator.create(SimpleEntity.class); // Creator.create(10, SimpleEntity.class);
        SimpleEntity entry = creator.create();
        System.out.println(entry);
        for (int i = 0; i < 10000000; i++) {
            creator.create();
        }
        System.gc();
        Thread.sleep(2000);
        System.out.println(creator.create());
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public InetSocketAddress getAddr() {
        return addr;
    }

    public void setAddr(InetSocketAddress addr) {
        this.addr = addr;
    }

    public int[] getAddrs() {
        return addrs;
    }

    public void setAddrs(int[] addrs) {
        this.addrs = addrs;
    }

    public List<String> getLists() {
        return lists;
    }

    public void setLists(List<String> lists) {
        this.lists = lists;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Map<String, Integer> getMap() {
        return map;
    }

    public void setMap(Map<String, Integer> map) {
        this.map = map;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String[] getStrings() {
        return strings;
    }

    public void setStrings(String[] strings) {
        this.strings = strings;
    }
}
