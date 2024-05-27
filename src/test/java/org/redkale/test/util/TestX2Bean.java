/*
 *
 */
package org.redkale.test.util;

import java.util.Map;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class TestX2Bean implements TestInterface {

    private String name;

    private int id;

    private Map<String, String> map;

    public int remark;

    private String seqno;

    public String getSeqno() {
        return seqno;
    }

    public void setSeqno(String seqno) {
        this.seqno = seqno;
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

    public Map<String, String> getMap() {
        return map;
    }

    public void setMap(Map<String, String> map) {
        this.map = map;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
