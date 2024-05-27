/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.Map;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class TestBean extends TestABean implements TestInterface {

    private String name;

    private int id;

    private Map<String, String> map;

    public String remark;

    private Long seqno;

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

    public Long getSeqno() {
        return seqno;
    }

    public void setSeqno(Long seqno) {
        this.seqno = seqno;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
