/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.List;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 */
public class ComplextEntity extends BasedEntity {

    @Id
    private int userid;

    private String chname = "";

    @Transient
    private boolean flag = true;

    @Transient
    private List<SimpleChildEntity> children;

    @Transient
    private SimpleEntity user;

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getChname() {
        return chname;
    }

    public void setChname(String chname) {
        this.chname = chname;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public List<SimpleChildEntity> getChildren() {
        return children;
    }

    public void setChildren(List<SimpleChildEntity> children) {
        this.children = children;
    }

    public SimpleEntity getUser() {
        return user;
    }

    public void setUser(SimpleEntity user) {
        this.user = user;
    }

}
