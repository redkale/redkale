/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import org.redkale.convert.bson.BsonFactory;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Id;
import org.redkale.source.FilterBean;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class SncpTestBean implements FilterBean {

    @Id
    private long id;

    private String content;

    public static void main(String[] args) throws Exception {
        String json = "{\"content\":\"数据: 01\",\"id\":1}";
        SncpTestBean bean = JsonConvert.root().convertFrom(SncpTestBean.class, json);
        System.out.println(bean);
        byte[] bs = BsonFactory.root().getConvert().convertTo(bean);
        Utility.println("---------", bs);
        System.out.println(BsonFactory.root().getConvert().convertFrom(SncpTestBean.class, bs).toString());
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
