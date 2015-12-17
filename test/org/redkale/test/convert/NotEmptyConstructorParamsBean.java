/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.beans.*;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;

/**
 *
 * @author zhangjx
 */
public class NotEmptyConstructorParamsBean {

    private final int userid;

    private String name;

    private long createtime;

    @ConstructorProperties({"userid", "name"})
    public NotEmptyConstructorParamsBean(int userid, String name) {
        this.userid = userid;
        this.name = name;
    }

    public static void main(String[] args) throws Exception {
        final JsonConvert jsonConvert = JsonFactory.root().getConvert();
        final BsonConvert bsonConvert = BsonFactory.root().getConvert();
        NotEmptyConstructorParamsBean bean = new NotEmptyConstructorParamsBean(12345678, "哈哈");
        bean.setCreatetime(System.currentTimeMillis());
        String json = jsonConvert.convertTo(bean);
        System.out.println(json);
        System.out.println(jsonConvert.convertFrom(NotEmptyConstructorParamsBean.class, json).toString());
        byte[] bytes = bsonConvert.convertTo(bean);
        System.out.println(bsonConvert.convertFrom(NotEmptyConstructorParamsBean.class, bytes).toString());
    }

    public int getUserid() {
        return userid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
