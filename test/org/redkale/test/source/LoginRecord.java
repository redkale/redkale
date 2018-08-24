/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.io.Serializable;
import javax.persistence.*;
import org.redkale.source.*;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
@DistributeTable(strategy = LoginRecord.TableStrategy.class)
public class LoginRecord extends BaseEntity {

    @Id
    @Column(comment = "主键ID; 值=create36time(9位)+'-'+UUID(32位)")
    private String loginid = ""; //主键ID; 值=create36time(9位)+'-'+UUID(32位)

    @Column(updatable = false, comment = "C端用户ID")
    private long userid; //C端用户ID

    @Column(updatable = false, comment = "登录网络类型; wifi/4g/3g")
    private String netmode = ""; //登录网络类型; wifi/4g/3g

    @Column(updatable = false, comment = "APP版本信息")
    private String appversion = ""; //APP版本信息

    @Column(updatable = false, comment = "APP操作系统信息")
    private String appos = ""; //APP操作系统信息

    @Column(updatable = false, comment = "登录时客户端信息")
    private String loginagent = ""; //登录时客户端信息

    @Column(updatable = false, comment = "登录时的IP")
    private String loginaddr = ""; //登录时的IP

    @Column(updatable = false, comment = "创建时间")
    private long createtime; //创建时间

    /** 以下省略getter setter方法 */
    //
    public void setLoginid(String loginid) {
        this.loginid = loginid;
    }

    public String getLoginid() {
        return this.loginid;
    }

    public void setUserid(long userid) {
        this.userid = userid;
    }

    public long getUserid() {
        return this.userid;
    }

    public void setNetmode(String netmode) {
        this.netmode = netmode;
    }

    public String getNetmode() {
        return this.netmode;
    }

    public String getAppversion() {
        return appversion;
    }

    public void setAppversion(String appversion) {
        this.appversion = appversion;
    }

    public String getAppos() {
        return appos;
    }

    public void setAppos(String appos) {
        this.appos = appos;
    }

    public void setLoginagent(String loginagent) {
        this.loginagent = loginagent;
    }

    public String getLoginagent() {
        return this.loginagent;
    }

    public void setLoginaddr(String loginaddr) {
        this.loginaddr = loginaddr;
    }

    public String getLoginaddr() {
        return this.loginaddr;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    public long getCreatetime() {
        return this.createtime;
    }

    private static DataSource source;

    //创建对象
    public static void main(String[] args) throws Throwable {
        LoginRecord record = new LoginRecord();
        long now = System.currentTimeMillis();
        record.setCreatetime(now); //设置创建时间
        record.setLoginid(Utility.format36time(now) + "-" + Utility.uuid());  //主键的生成规则
        //....  填充其他字段
        source.insert(record);
    }

    public static class TableStrategy implements DistributeTableStrategy<LoginRecord> {

        private static final String dayformat = "%1$tY%1$tm%1$td"; //一天一个表

        private static final String yearformat = "%1$tY";  //一年一个库

        //过滤查询时调用本方法
        @Override
        public String getTable(String table, FilterNode node) {
            Serializable day = node.findValue("#day");  //LoginRecord没有day字段，所以前面要加#，表示虚拟字段, 值为yyyyMMdd格式
            if (day != null) getTable(table, (Integer) day, 0L); //存在#day参数则直接使用day值
            Serializable time = node.findValue("createtime");  //存在createtime则使用最小时间，且createtime的范围必须在一天内，因为本表以天为单位建表
            return getTable(table, 0, (time == null ? 0L : (time instanceof Range ? ((Range.LongRange) time).getMin() : (Long) time)));
        }

        //创建或单个查询时调用本方法
        @Override
        public String getTable(String table, LoginRecord bean) {
            return getTable(table, 0, bean.getCreatetime());
        }

        //根据主键ID查询单个记录时调用本方法
        @Override
        public String getTable(String table, Serializable primary) {
            String id = (String) primary;
            return getTable(table, 0, Long.parseLong(id.substring(0, 9), 36));
        }

        private String getTable(String table, int day, long createtime) { //day为0或yyyyMMdd格式数据
            int pos = table.indexOf('.');
            String year = day > 0 ? String.valueOf(day / 10000) : String.format(yearformat, createtime); //没有day取createtime
            return "platf_login_" + year + "." + table.substring(pos + 1) + "_" + (day > 0 ? day : String.format(dayformat, createtime));
        }
    }
}
