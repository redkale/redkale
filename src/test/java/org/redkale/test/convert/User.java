/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert;

import java.util.Date;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class User {
    @ConvertColumn(index = 3)
    private Long id;

    @ConvertColumn(index = 4)
    private String name;

    @ConvertColumn(index = 5)
    private String nickName;

    @ConvertColumn(index = 1)
    private Integer age;

    @ConvertColumn(index = 6)
    private String sex;

    @ConvertColumn(index = 2)
    private Date createTime;

    public static User create() {
        User user = new User();
        user.setId(1L);
        user.setName("Hello");
        user.setAge(18);
        user.setSex("男");
        user.setNickName("测试号");
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

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
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
