/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.io.Serializable;
import java.util.*;
import javax.persistence.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.*;

/**
 *
 * @author zhangjx
 */
//@Cacheable
public class JsonRecord {

    @SourceConvert
    private static JsonConvert createConvert() {
        return JsonConvert.root();
    }

    @Id
    @Column(comment = "主键ID;")
    private long recordid;

    @Column(comment = ";")
    private String recordname = "";

    @Column(comment = ";")
    private Map<String, Integer> rmap;

    @Column(comment = ";")
    private List<String> rlist;

    @Column(comment = ";")
    private Set<String> rset;

    public static JsonRecord create() {
        JsonRecord record = new JsonRecord();
        record.setRecordid(System.currentTimeMillis());
        record.setRecordname("my name");
        Map<String, Integer> map = new HashMap<>();
        map.put("str111", 10000);
        map.put("str222", 20000);
        record.setRmap(map);
        List<String> list = new ArrayList<>();
        list.add("item11");
        list.add("item22");
        list.add("item11");
        record.setRlist(list);
        Set<String> set = new HashSet<>();
        set.add("r1");
        set.add("r2");
        record.setRset(set);
        return record;
    }

    public static void main(String[] args) throws Throwable {
        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", "jdbc:mysql://localhost:3306/center?characterEncoding=utf8&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true");
        properties.put("javax.persistence.jdbc.user", "root");
        properties.put("javax.persistence.jdbc.password", "");
        DataSource source = DataSources.createDataSource("", properties);
        JsonRecord record = JsonRecord.create();
        source.insert(record);
        source.updateColumn(JsonRecord.class, record.getRecordid(), ColumnValue.mov("recordname", "my name 2"));
        record.getRmap().put("haha", 2222);
        source.updateColumn(JsonRecord.class, record.getRecordid(), ColumnValue.mov("rmap", (Serializable) (Object) record.getRmap()));
        System.out.println(source.find(JsonRecord.class, record.getRecordid()));
        System.out.println(source.findColumn(JsonRecord.class, "rmap", record.getRecordid()));
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public long getRecordid() {
        return recordid;
    }

    public void setRecordid(long recordid) {
        this.recordid = recordid;
    }

    public String getRecordname() {
        return recordname;
    }

    public void setRecordname(String recordname) {
        this.recordname = recordname;
    }

    public Map<String, Integer> getRmap() {
        return rmap;
    }

    public void setRmap(Map<String, Integer> rmap) {
        this.rmap = rmap;
    }

    public List<String> getRlist() {
        return rlist;
    }

    public void setRlist(List<String> rlist) {
        this.rlist = rlist;
    }

    public Set<String> getRset() {
        return rset;
    }

    public void setRset(Set<String> rset) {
        this.rset = rset;
    }

}
