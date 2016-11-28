/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.io.Serializable;
import javax.persistence.*;
import org.redkale.source.*;

/**
 *
 * @author zhangjx
 */
@DistributeTable(strategy = LoginUserRecord.TableStrategy.class)
public class LoginUserRecord extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(comment = "UUID")
    private String seqid = ""; //UUID

    @Column(updatable = false, comment = "C端用户ID")
    private long userid; //C端用户ID

    @Column(comment = "LoginRecord主键")
    private String loginid = ""; //LoginRecord主键

    @Column(updatable = false, comment = "创建时间")
    private long createtime; //创建时间

    /** 以下省略getter setter方法 */
    //
    public String getSeqid() {
        return seqid;
    }

    public void setSeqid(String seqid) {
        this.seqid = seqid;
    }

    public long getUserid() {
        return userid;
    }

    public void setUserid(long userid) {
        this.userid = userid;
    }

    public String getLoginid() {
        return loginid;
    }

    public void setLoginid(String loginid) {
        this.loginid = loginid;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    public static class TableStrategy implements DistributeTableStrategy<LoginUserRecord> {

        @Override
        public String getTable(String table, LoginUserRecord bean) {
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
            return "platf_login." + table.substring(pos + 1) + "_" + (hash > 9 ? hash : ("0" + hash));
        }

    }
}
