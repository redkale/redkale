/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.*;
import org.redkale.convert.bson.BsonFactory;

/**
 *
 * @author zhangjx
 */
public class Two extends One {

    public Two() {
        super(90100119);
    }
    
   protected List<String> list;

  protected  Map<String, String> stringMap;
    
  protected  List<ConvertRecord> records;

  protected  Map<String, ConvertRecord> recordMap;
    
    public Map<String, String> getStringMap() {
        return stringMap;
    }

    public void setStringMap(Map<String, String> stringMap) {
        this.stringMap = stringMap;
    }

    String ip;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<String> getList() {
        return list;
    }

    public void setList(List<String> list) {
        this.list = list;
    }

    public List<ConvertRecord> getRecords() {
        return records;
    }

    public void setRecords(List<ConvertRecord> records) {
        this.records = records;
    }

    public Map<String, ConvertRecord> getRecordMap() {
        return recordMap;
    }

    public void setRecordMap(Map<String, ConvertRecord> recordMap) {
        this.recordMap = recordMap;
    }
    
    public static void main(String[] args) throws Throwable {
        Two  two = new Two();
        two.setKey("key111");
        two.setCode(12345); 
        List<String> list = new ArrayList<>();
        list.add("haha");
        two.setList(list); 
        Map<String, String> map = new HashMap<>();
        map.put("222", "333");
        two.setStringMap(map);
        
        List<ConvertRecord> records = new ArrayList<>();
        records.add(ConvertRecord.createDefault());
        two.setRecords(records);
        
        Map<String, ConvertRecord> rmap = new HashMap<>();
        rmap.put("222", ConvertRecord.createDefault());
        two.setRecordMap(rmap);
        
        byte[]  bs = BsonFactory.root().getConvert().convertTo(two);
        
        One one =BsonFactory.root().getConvert().convertFrom(One.class, bs);
        System.out.println(one);
    }
    
}