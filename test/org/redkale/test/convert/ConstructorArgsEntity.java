/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.util.ConstructorParameters;

/**
 * 测试不存在无参数的构造函数的bean类解析
 *
 * @author zhangjx
 */
public class ConstructorArgsEntity {

    private final int userid;

    private String name;

    private long createtime;

    @ConstructorParameters({"userid", "name"})
    public ConstructorArgsEntity(int userid, String name) {
        this.userid = userid;
        this.name = name;
    }

    public static void main(String[] args) throws Exception {
        final JsonConvert jsonConvert = JsonConvert.root();
        final BsonConvert bsonConvert = BsonFactory.root().getConvert();
        ConstructorArgsEntity bean = new ConstructorArgsEntity(12345678, "哈哈");
        bean.setCreatetime(System.currentTimeMillis());
        String json = jsonConvert.convertTo(bean);
        System.out.println(json);
        System.out.println(jsonConvert.convertFrom(ConstructorArgsEntity.class, json).toString());
        byte[] bytes = bsonConvert.convertTo(bean);
        System.out.println(bsonConvert.convertFrom(ConstructorArgsEntity.class, bytes).toString());
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
        return JsonConvert.root().convertTo(this);
    }
}
