/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.source.DistributeGenerator;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 */
public class UserMentorPrize  extends BasedEntity implements Comparable<UserMentorPrize> {

    private static final long serialVersionUID = 1L;

    @Id
    @DistributeGenerator(initialValue = 10001)
    private long mentorprizeid;

    private int userid;

    private String prizename;

    private int happenday;

    @Column(updatable = false)
    private long createtime = System.currentTimeMillis();

    private long updatetime;

    public UserMentorPrize() {
    }

    public long getMentorprizeid() {
        return mentorprizeid;
    }

    public void setMentorprizeid(long mentorprizeid) {
        this.mentorprizeid = mentorprizeid;
    }

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getPrizename() {
        return prizename;
    }

    public void setPrizename(String prizename) {
        this.prizename = prizename;
    }

    public int getHappenday() {
        return happenday;
    }

    public void setHappenday(int happenday) {
        this.happenday = happenday;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    public long getUpdatetime() {
        return updatetime;
    }

    public void setUpdatetime(long updatetime) {
        this.updatetime = updatetime;
    }

    @Override
    public int compareTo(UserMentorPrize o) {
        return this.happenday - o.happenday;
    }
}
