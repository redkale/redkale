package org.redkale.test.rest;

import javax.persistence.Id;
import org.redkale.convert.json.JsonFactory;

/**
 * 当前用户对象
 *
 * @author zhangjx
 */
public class UserInfo {

    @Id
    private int userid;

    private String username = "";

    public int getUserid() {
        return userid;
    }

    public boolean checkAuth(int moduleid, int actionid) {
        if (moduleid == 0 || actionid == 0) return true;
        //权限判断
        return true;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
