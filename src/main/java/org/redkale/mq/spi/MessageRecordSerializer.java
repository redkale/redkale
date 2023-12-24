/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * MessageRecord的MessageCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class MessageRecordSerializer implements MessageCoder<MessageRecord> {

    private static final MessageRecordSerializer instance = new MessageRecordSerializer();

    public static MessageRecordSerializer getInstance() {
        return instance;
    }

    //消息内容的类型
    @Override
    public byte ctype() {
        return 0;
    }

    @Override
    public byte[] encode(MessageRecord data) {
        if (data == null) {
            return null;
        }
        byte[] userid = MessageCoder.encodeUserid(data.getUserid());
        byte[] groupid = MessageCoder.getBytes(data.getGroupid());
        byte[] topic = MessageCoder.getBytes(data.getTopic());
        byte[] respTopic = MessageCoder.getBytes(data.getRespTopic());
        byte[] traceid = MessageCoder.getBytes(data.getTraceid());
        int count = 8 //seqid
            + 1 //ctype
            + 4 //version
            + 4 //flag
            + 8 //createTime
            + 2 + userid.length
            + 2 + groupid.length
            + 2 + topic.length
            + 2 + respTopic.length
            + 2 + traceid.length
            + 4 + (data.getContent() == null ? 0 : data.getContent().length);
        final byte[] bs = new byte[count];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.putLong(data.getSeqid());
        buffer.put(data.ctype);
        buffer.putInt(data.getVersion());
        buffer.putInt(data.getFlag());
        buffer.putLong(data.getCreateTime());

        buffer.putChar((char) userid.length);
        if (userid.length > 0) {
            buffer.put(userid);
        }

        buffer.putChar((char) groupid.length);
        if (groupid.length > 0) {
            buffer.put(groupid);
        }

        buffer.putChar((char) topic.length);
        if (topic.length > 0) {
            buffer.put(topic);
        }

        buffer.putChar((char) respTopic.length);
        if (respTopic.length > 0) {
            buffer.put(respTopic);
        }

        buffer.putChar((char) traceid.length);
        if (traceid.length > 0) {
            buffer.put(traceid);
        }

        if (data.getContent() == null) {
            buffer.putInt(0);
        } else {
            buffer.putInt(data.getContent().length);
            buffer.put(data.getContent());
        }
        return bs;
    }

    @Override
    public MessageRecord decode(byte[] data) {
        if (data == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long seqid = buffer.getLong();
        byte ctype = buffer.get();
        int version = buffer.getInt();
        int flag = buffer.getInt();
        long createTime = buffer.getLong();

        Serializable userid = MessageCoder.decodeUserid(buffer);
        String groupid = MessageCoder.getSmallString(buffer);
        String topic = MessageCoder.getSmallString(buffer);
        String respTopic = MessageCoder.getSmallString(buffer);
        String traceid = MessageCoder.getSmallString(buffer);

        byte[] content = null;
        int contentlen = buffer.getInt();
        if (contentlen > 0) {
            content = new byte[contentlen];
            buffer.get(content);
        }
        return new MessageRecord(seqid, ctype, version, flag, createTime, userid, groupid, topic, respTopic, traceid, content);
    }

}
