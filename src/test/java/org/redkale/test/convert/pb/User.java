/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.util.Date;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class User {
    @ConvertColumn(index = 1)
    private Long id;

    @ConvertColumn(index = 2)
    private String name;

    @ConvertColumn(index = 3)
    private String trueName;

    @ConvertColumn(index = 4)
    private Integer age;

    @ConvertColumn(index = 5)
    private String sex;

    @ConvertColumn(index = 6)
    private Date createTime;

    public static User create() {
        User user = new User();
        user.setId(1L);
        user.setName("赵侠客"); //
        user.setAge(29);
        user.setSex("男");
        user.setTrueName("公众号");
        user.setCreateTime(new Date(1451577600000L));
        return user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrueName() {
        return trueName;
    }

    public void setTrueName(String trueName) {
        this.trueName = trueName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
