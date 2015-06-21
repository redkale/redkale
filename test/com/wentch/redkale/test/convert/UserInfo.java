/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

/**
 *
 * @author zhangjx
 */
public class UserInfo extends BasedEntity {

    private int userid;

    private String namekey;

    private String nickname;

    private String chname;

    private String password;

    private String mobile;

    private String email;

    private short type;

    private short status;

    private int gmt = 8;

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getNamekey() {
        return namekey;
    }

    public void setNamekey(String namekey) {
        this.namekey = namekey;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getChname() {
        return chname;
    }

    public void setChname(String chname) {
        this.chname = chname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public short getType() {
        return type;
    }

    public void setType(short type) {
        this.type = type;
    }

    public short getStatus() {
        return status;
    }

    public void setStatus(short status) {
        this.status = status;
    }

    public int getGmt() {
        return gmt;
    }

    public void setGmt(int gmt) {
        this.gmt = gmt;
    }

}
