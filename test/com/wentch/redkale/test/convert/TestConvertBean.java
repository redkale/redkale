/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.util.Sheet;
import com.wentch.redkale.util.TypeToken;
import com.wentch.redkale.convert.bson.BsonFactory;
import com.wentch.redkale.convert.json.JsonFactory;
import java.lang.reflect.*;
import java.util.*;

/**
 *
 * @author zhangjx
 * @param <T>
 * @param <K>
 * @param <V>
 */
public class TestConvertBean<T, K, V> {

    public static class Entry {

        private String id;

        private String remark;

        public Entry(){
            
        }
        public Entry(String id, String remark) {
            this.id = id;
            this.remark = remark;
        }

        @Override
        public String toString() {
            return "Entry{" + "id=" + id + ", remark=" + remark + '}';
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

    }

    private T name;

    private List<? extends K> list;

    private Map<K, V> map;

    public static void main(String[] args) throws Throwable {
        TestConvertBean<Long, String, Entry> bean = new TestConvertBean<>();
        bean.setName(1234567890L);
        List<String> list = new ArrayList<>();
        list.add("你好");
        bean.setList(list);
        Map<String, Entry> map = new HashMap<>();
        map.put("myvalue", new Entry("myid", ""));
        bean.setMap(map);
        final Type type = new TypeToken<TestConvertBean<Long, String, Entry>>() {
        }.getType();
        JsonFactory.root().setTiny(true); 
        String json = JsonFactory.root().getConvert().convertTo(type, bean);
        System.out.println(json); 
        System.out.println( JsonFactory.root().getConvert().convertFrom(type, json).toString());
//        JsonFactory child = JsonFactory.root().createChild();
//        System.out.println(child.register(TestConvertBean.class, "name", new ConvertColumnEntry("name", true)));
//        child.register(TestConvertBean.class, child.createEncoder(type)); 
//        System.out.println(child.getConvert().convertTo(type, bean));
        if(true) return;
        
        Sheet<Entry> sheet = new Sheet<>();
        sheet.setTotal(20);
        List<Entry> list2 = new ArrayList<>();
        list2.add(new Entry("myid", "描述"));
        sheet.setRows(list2); 
         final Type type2 = new TypeToken<Sheet<Entry>>() {
        }.getType();
         System.out.println(JsonFactory.root().getConvert().convertTo(type2, sheet)); 
         sheet = BsonFactory.root().getConvert().convertFrom(type2, BsonFactory.root().getConvert().convertTo(type2, sheet));
         System.out.println(JsonFactory.root().getConvert().convertTo(type2, sheet));
    }

    @Override
    public String toString() {
        return "TestConvertBean{" + "name=" + name + ", list=" + list + ", map=" + map + '}';
    }

    public T getName() {
        return name;
    }

    public void setName(T name) {
        this.name = name;
    }

    public List<? extends K> getList() {
        return list;
    }

    public void setList(List<? extends K> list) {
        this.list = list;
    }

    public Map<K, V> getMap() {
        return map;
    }

    public void setMap(Map<K, V> map) {
        this.map = map;
    }

}
