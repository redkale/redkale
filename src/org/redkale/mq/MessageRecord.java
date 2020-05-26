/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Comment;

/**
 * 存在MQ里面的数据结构
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class MessageRecord implements Serializable {

    @Comment("消息序列号")
    protected long seqid;

    @Comment("标记位, 自定义时使用")
    protected int flag;

    @Comment("用户ID，无用户信息视为0")
    protected int userid;

    @Comment("组ID")
    protected String groupid;

    @Comment("当前topic")
    protected String topic;

    @Comment("目标topic, 为空表示无目标topic")
    protected String resptopic;

    @Comment("消息内容")
    protected byte[] content;

    public MessageRecord() {
    }

    public MessageRecord(long seqid, String topic, String resptopic, byte[] content) {
        this(seqid, 0, 0, null, topic, resptopic, content);
    }

    public MessageRecord(long seqid, int userid, String groupid, String topic, String resptopic, byte[] content) {
        this(seqid, 0, userid, groupid, topic, resptopic, content);
    }

    public MessageRecord(long seqid, int flag, int userid, String groupid, String topic, String resptopic, byte[] content) {
        this.seqid = seqid;
        this.flag = flag;
        this.userid = userid;
        this.groupid = groupid;
        this.topic = topic;
        this.resptopic = resptopic;
        this.content = content;
    }

    public String contentUTF8String() {
        return content == null ? null : new String(content, StandardCharsets.UTF_8);
    }

    @ConvertDisabled
    public boolean isEmptyTopic() {
        return this.topic == null || this.topic.isEmpty();
    }

    @ConvertDisabled
    public boolean isEmptyResptopic() {
        return this.resptopic == null || this.resptopic.isEmpty();
    }

    public MessageRecord flag(int flag) {
        this.flag = flag;
        return this;
    }

    public MessageRecord userid(int userid) {
        this.userid = userid;
        return this;
    }

    public MessageRecord groupid(String groupid) {
        this.groupid = groupid;
        return this;
    }

    public MessageRecord topic(String topic) {
        this.topic = topic;
        return this;
    }

    public MessageRecord resptopic(String resptopic) {
        this.resptopic = resptopic;
        return this;
    }

    public MessageRecord content(byte[] content) {
        this.content = content;
        return this;
    }

    public MessageRecord contentUTF8(String content) {
        this.content = content == null ? null : content.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public long getSeqid() {
        return seqid;
    }

    public void setSeqid(long seqid) {
        this.seqid = seqid;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getGroupid() {
        return groupid;
    }

    public void setGroupid(String groupid) {
        this.groupid = groupid;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getResptopic() {
        return resptopic;
    }

    public void setResptopic(String resptopic) {
        this.resptopic = resptopic;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
