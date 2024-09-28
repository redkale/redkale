/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.bson;

import java.util.*;

/** @author redkale */
public class ConvertRecord {

    private String aname;

    private String desc = "";

    private int id = (int) System.currentTimeMillis();

    private int[] integers;

    private long[] longs;

    private List<String> strings;

    private Map<String, Integer> map;

    public static ConvertRecord createDefault() {
        ConvertRecord v = new ConvertRecord();
        v.setAname("this is name\n \"test");
        v.setId(1000000001);
        v.setIntegers(new int[] {12, 34, 56, 78, 90, 123, 456, 789});
        v.setLongs(new long[] {
            10000012L, 10000034L, 10000056L, 10000078L, -10000090L, -100000123L, -100000456L, -100000789L
        });
        List<String> list = new ArrayList<>();
        list.add("str_a");
        list.add("str_b");
        list.add("str_c");
        v.setStrings(list);
        Map<String, Integer> map = new HashMap<>();
        map.put("key_a", 111);
        map.put("key_b", 222);
        map.put("key_c", 333);
        v.setMap(map);
        return v;
    }

    public static void main(String[] args) throws Exception {
        final ConvertRecord entry = ConvertRecord.createDefault();
        run(ConvertRecord.class, entry);
    }

    public static <T> void run(final Class<T> type, final T entry) throws Exception {
        /**
         * final org.redkale.convert.json.JsonConvert convert =
         * org.redkale.convert.json.JsonFactory.root().getConvert(); final String entryString =
         * convert.convertTo(entry); convert.convertFrom(type, entryString); System.out.println("redkale-convert: " +
         * convert.convertTo(entry));
         *
         * <p>com.alibaba.fastjson.JSON.parseObject(entryString, type); System.out.println("fastjson 1.2.7: " +
         * com.alibaba.fastjson.JSON.toJSONString(entry));
         *
         * <p>final com.fasterxml.jackson.databind.ObjectMapper mapper = new
         * com.fasterxml.jackson.databind.ObjectMapper(); mapper.readValue(entryString, type);
         * System.out.println("jackson 2.7.2: " + mapper.writeValueAsString(entry));
         *
         * <p>final com.google.gson.Gson gson = new com.google.gson.Gson(); gson.fromJson(entryString, type);
         * System.out.println("google-gson 2.4: " + gson.toJson(entry));
         *
         * <p>System.out.println("------------------------------------------------"); System.out.println("组件 序列化耗时(ms)
         * 反序列化耗时(ms)"); final int count = 10_0000; long s = System.currentTimeMillis(); for (int i = 0; i < count; i++)
         * { convert.convertTo(entry); } long e = System.currentTimeMillis() - s; System.out.print("redkale-convert " +
         * e);
         *
         * <p>s = System.currentTimeMillis(); for (int i = 0; i < count; i++) { convert.convertFrom(type, entryString);
         * } e = System.currentTimeMillis() - s; System.out.println("\t " + e);
         *
         * <p>//---------------------------------------------------------------------------- s =
         * System.currentTimeMillis(); for (int i = 0; i < count; i++) { com.alibaba.fastjson.JSON.toJSONString(entry);
         * } e = System.currentTimeMillis() - s; System.out.print("fastjson 1.2.7 " + e);
         *
         * <p>s = System.currentTimeMillis(); for (int i = 0; i < count; i++) {
         * com.alibaba.fastjson.JSON.parseObject(entryString, type); } e = System.currentTimeMillis() - s;
         * System.out.println("\t " + e); //----------------------------------------------------------------------------
         * s = System.currentTimeMillis(); for (int i = 0; i < count; i++) { mapper.writeValueAsString(entry); } e =
         * System.currentTimeMillis() - s; System.out.print("jackson 2.7.2 " + e);
         *
         * <p>s = System.currentTimeMillis(); for (int i = 0; i < count; i++) { mapper.readValue(entryString, type); } e
         * = System.currentTimeMillis() - s; System.out.println("\t " + e);
         * //---------------------------------------------------------------------------- s =
         * System.currentTimeMillis(); for (int i = 0; i < count; i++) { gson.toJson(entry); } e =
         * System.currentTimeMillis() - s; System.out.print("google-gson 2.4 " + e);
         *
         * <p>s = System.currentTimeMillis(); for (int i = 0; i < count; i++) { gson.fromJson(entryString, type); } e =
         * System.currentTimeMillis() - s; System.out.println("\t " + e);
         * //----------------------------------------------------------------------------
         */
    }

    public String getAname() {
        return aname;
    }

    public void setAname(String aname) {
        this.aname = aname;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int[] getIntegers() {
        return integers;
    }

    public void setIntegers(int[] integers) {
        this.integers = integers;
    }

    public long[] getLongs() {
        return longs;
    }

    public void setLongs(long[] longs) {
        this.longs = longs;
    }

    public List<String> getStrings() {
        return strings;
    }

    public void setStrings(List<String> strings) {
        this.strings = strings;
    }

    public Map<String, Integer> getMap() {
        return map;
    }

    public void setMap(Map<String, Integer> map) {
        this.map = map;
    }
}
