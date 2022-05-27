/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

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
public class MessageRecordCoder implements MessageCoder<MessageRecord> {

    private static final MessageRecordCoder instance = new MessageRecordCoder();

    public static MessageRecordCoder getInstance() {
        return instance;
    }

    @Override
    public byte[] encode(MessageRecord data) {
        if (data == null) return null;
        byte[] userid = MessageCoder.encodeUserid(data.getUserid());
        byte[] groupid = MessageCoder.getBytes(data.getGroupid());
        byte[] topic = MessageCoder.getBytes(data.getTopic());
        byte[] resptopic = MessageCoder.getBytes(data.getRespTopic());
        byte[] traceid = MessageCoder.getBytes(data.getTraceid());
        int count = 8 //seqid
            + 1 //ctype
            + 4 //version
            + 4 //flag
            + 8 //createtime
            + 2 + userid.length
            + 2 + groupid.length
            + 2 + topic.length
            + 2 + resptopic.length
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
        if (userid.length > 0) buffer.put(userid);
        
        buffer.putChar((char) groupid.length);
        if (groupid.length > 0) buffer.put(groupid);
        
        buffer.putChar((char) topic.length);
        if (topic.length > 0) buffer.put(topic);
        
        buffer.putChar((char) resptopic.length);
        if (resptopic.length > 0) buffer.put(resptopic);
        
        buffer.putChar((char) traceid.length);
        if (traceid.length > 0) buffer.put(traceid);
        
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
        if (data == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long seqid = buffer.getLong();
        byte ctype = buffer.get();
        int version = buffer.getInt();
        int flag = buffer.getInt();
        long createtime = buffer.getLong();

        Serializable userid = MessageCoder.decodeUserid(buffer);
        String groupid = MessageCoder.getShortString(buffer);
        String topic = MessageCoder.getShortString(buffer);
        String resptopic = MessageCoder.getShortString(buffer);
        String traceid = MessageCoder.getShortString(buffer);

        byte[] content = null;
        int contentlen = buffer.getInt();
        if (contentlen > 0) {
            content = new byte[contentlen];
            buffer.get(content);
        }
        return new MessageRecord(seqid, ctype, version, flag, createtime, userid, groupid, topic, resptopic, traceid, content);
    }

}
