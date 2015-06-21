/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.convert.json.JsonFactory;
import com.wentch.redkale.util.Creator;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class TestEntry {

    private String name;

    private String desc="";
    
    private int id = (int) System.currentTimeMillis();

    private int[] addrs;

    private List<String> lists;

    private Map<String, Integer> map;

    public static TestEntry create() {
        TestEntry v = new TestEntry();
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
        return v;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(JsonFactory.root().getConvert().convertTo(create()));
        Creator<TestEntry> creator = Creator.create(TestEntry.class); //Creator.create(10, TestEntry.class);
        TestEntry entry = creator.create();
        System.out.println(entry);
        for(int i =0; i < 10000000; i++){
            creator.create();
        }
        System.gc();
        Thread.sleep(2000) ;
        System.out.println(creator.create());
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
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

}
