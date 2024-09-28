/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.bson;

import java.util.*;

/** @author zhangjx */
public class Two extends One {

    public Two() {
        super(90100119);
    }

    protected List<String> list;

    protected Map<String, String> stringMap;

    protected List<ConvertRecord> records;

    protected Map<String, ConvertRecord> recordMap;

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
}
