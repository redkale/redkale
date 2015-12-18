/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.redkale.util.TypeToken;
import org.redkale.convert.json.JsonFactory;
import java.lang.reflect.*;
import java.util.*;

/**
 * 支持泛型的
 *
 * @author zhangjx
 * @param <T>
 * @param <K>
 * @param <V>
 */
public class GenericEntity<T, K, V> {

    private K name;

    private List<? extends T> list;

    private Entry<K, V> entry;

    public static void main(String[] args) throws Throwable {
        GenericEntity<Long, String, SimpleEntity> bean = new GenericEntity<>();
        bean.setName("你好");
        List<Long> list = new ArrayList<>();
        list.add(1234567890L);
        bean.setList(list);
        bean.setEntry(new Entry<>("aaaa", SimpleEntity.create()));
        final Type type = new TypeToken<GenericEntity<Long, String, SimpleEntity>>() {
        }.getType();
        JsonFactory.root().setTiny(true);
        String json = JsonFactory.root().getConvert().convertTo(bean);
        System.out.println(json);
        System.out.println(JsonFactory.root().getConvert().convertFrom(type, json).toString());
    }

    @Override
    public String toString() {
        return "{\"entry\":" + entry + ",\"list\":" + list + ",\"name\":\"" + name + "\"}";
    }

    public K getName() {
        return name;
    }

    public void setName(K name) {
        this.name = name;
    }

    public List<? extends T> getList() {
        return list;
    }

    public void setList(List<? extends T> list) {
        this.list = list;
    }

    public Entry<K, V> getEntry() {
        return entry;
    }

    public void setEntry(Entry<K, V> entry) {
        this.entry = entry;
    }

    public static class Entry<K, V> {

        private K key;

        private V value;

        public Entry() {
        }

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public K getKey() {
            return key;
        }

        public void setKey(K key) {
            this.key = key;
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }

    }
}
