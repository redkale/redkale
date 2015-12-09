/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import org.redkale.convert.bson.BsonFactory;
import org.redkale.util.Utility;
import org.redkale.source.FilterBean;
import org.redkale.convert.json.JsonFactory;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 */
public class SncpTestBean implements FilterBean {

    @Id
    private long id;

    private String content;

    public static void main(String[] args) throws Exception {
        SncpTestBean bean = JsonFactory.root().getConvert().convertFrom(SncpTestBean.class, "{\"content\":\"数据: 01\",\"id\":1}");
        System.out.println(bean);
        byte[] bs = BsonFactory.root().getConvert().convertTo(bean);
        Utility.println("---------", bs); 
        System.out.println(BsonFactory.root().getConvert().convertFrom(SncpTestBean.class, bs).toString());
    }
    
    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
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
