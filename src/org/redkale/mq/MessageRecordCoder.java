/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import org.redkale.convert.ConvertType;

/**
 * MessageRecord的MessageCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class MessageRecordCoder implements MessageCoder<MessageRecord> {

    private static final MessageRecordCoder instance = new MessageRecordCoder();

    public static MessageRecordCoder getInstance() {
        return instance;
    }

    @Override
    public byte[] encode(MessageRecord data) {
        if (data == null) return null;
        byte[] stopics = MessageCoder.getBytes(data.getTopic());
        byte[] dtopics = MessageCoder.getBytes(data.getResptopic());
        byte[] groupid = MessageCoder.getBytes(data.getGroupid());
        int count = 8 + 4 + 4 + 4 + 8 + 4 + 2 + stopics.length + 2 + dtopics.length + 2 + groupid.length + 4 + (data.getContent() == null ? 0 : data.getContent().length);
        final byte[] bs = new byte[count];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.putLong(data.getSeqid());
        buffer.putInt(data.getVersion());
        buffer.putInt(data.getFormat() == null ? 0 : data.getFormat().getValue());
        buffer.putInt(data.getFlag());
        buffer.putLong(data.getCreatetime());
        buffer.putInt(data.getUserid());
        buffer.putChar((char) groupid.length);
        if (groupid.length > 0) buffer.put(groupid);
        buffer.putChar((char) stopics.length);
        if (stopics.length > 0) buffer.put(stopics);
        buffer.putChar((char) dtopics.length);
        if (dtopics.length > 0) buffer.put(dtopics);
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
        int version = buffer.getInt();
        ConvertType format = ConvertType.find(buffer.getInt());
        int flag = buffer.getInt();
        long createtime = buffer.getLong();
        int userid = buffer.getInt();

        String groupid = MessageCoder.getShortString(buffer);
        String topic = MessageCoder.getShortString(buffer);
        String resptopic = MessageCoder.getShortString(buffer);

        byte[] content = null;
        int contentlen = buffer.getInt();
        if (contentlen > 0) {
            content = new byte[contentlen];
            buffer.get(content);
        }
        return new MessageRecord(seqid, version, format, flag,
            createtime, userid, groupid, topic, resptopic, content);
    }

}
