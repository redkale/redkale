/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import javax.persistence.*;
import org.redkale.convert.json.*;

/**
 * CREATE TABLE `LoginTestRecord` (
 * `sessionid` VARCHAR(64) NOT NULL COMMENT '登录会话ID',
 * `userid` INT(11) NOT NULL COMMENT '登录用户ID',
 * `loginagent` VARCHAR(128) NOT NULL COMMENT '登录端信息',
 * `loginip` VARCHAR(255) NOT NULL COMMENT '登录IP',
 * `logintime` BIGINT(20) NOT NULL COMMENT '登录时间',
 * `logouttime` BIGINT(20) NOT NULL COMMENT '注销时间',
 * PRIMARY KEY (`sessionid`)
 * ) ENGINE=INNODB DEFAULT CHARSET=utf8;
 *
 * @author zhangjx
 */
public class LoginTestRecord {

    @Id
    private String sessionid;

    private int userid;

    private String loginagent;

    private String loginip;

    private long logintime;

    private long logouttime;

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getLoginagent() {
        return loginagent;
    }

    public void setLoginagent(String loginagent) {
        this.loginagent = loginagent;
    }

    public String getLoginip() {
        return loginip;
    }

    public void setLoginip(String loginip) {
        this.loginip = loginip;
    }

    public long getLogintime() {
        return logintime;
    }

    public void setLogintime(long logintime) {
        this.logintime = logintime;
    }

    public long getLogouttime() {
        return logouttime;
    }

    public void setLogouttime(long logouttime) {
        this.logouttime = logouttime;
    }

}
