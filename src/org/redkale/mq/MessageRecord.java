/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import org.redkale.convert.*;
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

    @ConvertColumn(index = 1)
    @Comment("消息序列号")
    protected long seqid;

    @ConvertColumn(index = 2)
    @Comment("内容的格式， 只能是JSON、BSON、PROTOBUF、DIY和null, 普通文本也归于JSON")
    protected ConvertType format;

    @ConvertColumn(index = 3)
    @Comment("标记位, 自定义时使用")
    protected int flag;

    @ConvertColumn(index = 4)
    @Comment("用户ID，无用户信息视为0")
    protected int userid;

    @ConvertColumn(index = 5)
    @Comment("组ID")
    protected String groupid;

    @ConvertColumn(index = 6)
    @Comment("当前topic")
    protected String topic;

    @ConvertColumn(index = 7)
    @Comment("目标topic, 为空表示无目标topic")
    protected String resptopic;

    @ConvertColumn(index = 8)
    @Comment("消息内容")
    protected byte[] content;

    public MessageRecord() {
    }

    public MessageRecord(String resptopic, String content) {
        this(System.nanoTime(), content == null ? null : ConvertType.JSON, 0, 0, null, null, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord(String topic, String resptopic, String content) {
        this(System.nanoTime(), content == null ? null : ConvertType.JSON, 0, 0, null, topic, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord(int userid, String topic, String resptopic, String content) {
        this(System.nanoTime(), content == null ? null : ConvertType.JSON, 0, userid, null, topic, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord(ConvertType format, String topic, String resptopic, byte[] content) {
        this(System.nanoTime(), format, 0, 0, null, topic, resptopic, content);
    }

    public MessageRecord(long seqid, ConvertType format, String topic, String resptopic, byte[] content) {
        this(seqid, format, 0, null, topic, resptopic, content);
    }

    public MessageRecord(long seqid, ConvertType format, int userid, String groupid, String topic, String resptopic, byte[] content) {
        this(seqid, format, 0, userid, groupid, topic, resptopic, content);
    }

    public MessageRecord(String topic, String resptopic, Convert convert, Object bean) {
        this(0, null, topic, resptopic, convert, bean);
    }

    public MessageRecord(int userid, String topic, String resptopic, Convert convert, Object bean) {
        this(userid, null, topic, resptopic, convert, bean);
    }

    public MessageRecord(int userid, String groupid, String topic, String resptopic, Convert convert, Object bean) {
        this(0, userid, groupid, topic, resptopic, convert, bean);
    }

    public MessageRecord(int flag, int userid, String groupid, String topic, String resptopic, Convert convert, Object bean) {
        this(System.nanoTime(), convert.getFactory().getConvertType(), flag, userid, groupid, topic, resptopic, convert.convertToBytes(bean));
    }

    public MessageRecord(long seqid, ConvertType format, int flag, int userid, String groupid, String topic, String resptopic, byte[] content) {
        this.seqid = seqid;
        this.format = format;
        this.flag = flag;
        this.userid = userid;
        this.groupid = groupid;
        this.topic = topic;
        this.resptopic = resptopic;
        this.content = content;
    }

    public String contentString() {
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

    public MessageRecord format(ConvertType format) {
        this.format = format;
        return this;
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

    public MessageRecord contentString(String content) {
        this.content = content == null ? null : content.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public long getSeqid() {
        return seqid;
    }

    public void setSeqid(long seqid) {
        this.seqid = seqid;
    }

    public ConvertType getFormat() {
        return format;
    }

    public void setFormat(ConvertType format) {
        this.format = format;
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
        //return JsonConvert.root().convertTo(this);
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"seqid\":").append(this.seqid);
        if (this.format != null) sb.append(",\"format\":\"").append(this.format).append("\"");
        if (this.flag != 0) sb.append(",\"flag\":").append(this.flag);
        if (this.userid != 0) sb.append(",\"userid\":").append(this.userid);
        if (this.groupid != null) sb.append(",\"groupid\":\"").append(this.groupid).append("\"");
        if (this.topic != null) sb.append(",\"topic\":\"").append(this.topic).append("\"");
        if (this.resptopic != null) sb.append(",\"resptopic\":\"").append(this.resptopic).append("\"");
        if (this.content != null) sb.append(",\"content\":").append(this.format == ConvertType.JSON ? ("\"" + new String(this.content, StandardCharsets.UTF_8) + "\"") : JsonConvert.root().convertTo(this.content));
        sb.append("}");
        return sb.toString();
    }

//    public static void main(String[] args) throws Throwable {
//        System.out.println(new MessageRecord(333, ConvertType.JSON, 2, 3, null, "tt", null, "xxx".getBytes()));
//    }
}
