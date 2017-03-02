/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.io.Serializable;
import javax.persistence.*;
import org.redkale.convert.*;
import org.redkale.source.*;

/**
 *
 * @author zhangjx
 */
@DistributeTable(strategy = UserDetail.TableStrategy.class)
public class UserDetail extends BaseEntity {

    public static class TableStrategy implements DistributeTableStrategy<UserDetail> {

        @Override
        public String getTable(String table, UserDetail bean) {
            return getTable(table, bean.getUserid());
        }

        @Override
        public String getTable(String table, FilterNode node) {
            Serializable id = node.findValue("userid");
            if (id != null) return getTable(table, id);
            return getHashTable(table, (Integer) node.findValue("#hash"));
        }

        @Override
        public String getTable(String table, Serializable userid) {
            return getHashTable(table, (int) (((Long) userid) % 100));
        }

        private String getHashTable(String table, int hash) {
            int pos = table.indexOf('.');
            return "platf_user." + table.substring(pos + 1) + "_" + (hash > 9 ? hash : ("0" + hash));
        }

    }

    @Id
    private long userid; //用户ID

    @Column(length = 64, comment = "用户昵称")
    private String username = ""; //用户昵称

    @Column(length = 32, comment = "手机号码")
    private String mobile = ""; //手机号码

    @Column(length = 64, comment = "密码")
    @ConvertColumn(ignore = true, type = ConvertType.ALL)
    private String password = ""; //密码

    @Column(length = 128, comment = "备注")
    private String remark = ""; //备注

    @Column(updatable = false, comment = "创建时间")
    private long createtime; //创建时间

    /** 以下省略getter setter方法 */
    public long getUserid() {
        return userid;
    }

    public void setUserid(long userid) {
        this.userid = userid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }
}
