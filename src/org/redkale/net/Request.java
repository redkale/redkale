/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;

/**
 * 协议请求对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <C> Context子类型
 */
public abstract class Request<C extends Context> {

    protected final C context;

    protected final BsonConvert bsonConvert;

    protected final JsonConvert jsonConvert;

    protected long createtime;

    protected boolean keepAlive;

    protected int pipelineIndex;

    protected int pipelineCount;

    protected boolean pipelineOver;

    protected int hashid;

    protected AsyncConnection channel;

    /**
     * properties 与 attributes 的区别在于：调用recycle时， attributes会被清空而properties会保留;
     * properties 通常存放需要永久绑定在request里的一些对象
     */
    private final Map<String, Object> properties = new HashMap<>();

    protected final Map<String, Object> attributes = new HashMap<>();

    protected Request(C context) {
        this.context = context;
        this.bsonConvert = context.getBsonConvert();
        this.jsonConvert = context.getJsonConvert();
    }

    protected Request copyHeader() {
        return null;
    }

    protected Request pipeline(int pipelineIndex, int pipelineCount) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineCount = pipelineCount;
        return this;
    }

    /**
     * 返回值：Integer.MIN_VALUE: 帧数据； -1：数据不合法； 0：解析完毕； &gt;0: 需再读取的字节数。
     *
     * @param buffer ByteBuffer对象
     * @param last   同一Channel的上一个Request
     *
     * @return 缺少的字节数
     */
    protected abstract int readHeader(ByteBuffer buffer, Request last);

    protected abstract void prepare();

    protected void recycle() {
        hashid = 0;
        createtime = 0;
        pipelineIndex = 0;
        pipelineCount = 0;
        pipelineOver = false;
        keepAlive = false;
        attributes.clear();
        channel = null; // close it by response
    }

    protected <T> T setProperty(String name, T value) {
        properties.put(name, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    protected <T> T getProperty(String name) {
        return (T) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    protected <T> T removeProperty(String name) {
        return (T) properties.remove(name);
    }

    protected Map<String, Object> getProperties() {
        return properties;
    }

    protected InputStream newInputStream() {
        return channel.newInputStream();
    }

    public <T> T setAttribute(String name, T value) {
        attributes.put(name, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T removeAttribute(String name) {
        return (T) attributes.remove(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public ChannelContext getChannelContext() {
        return channel;
    }

    public C getContext() {
        return this.context;
    }

    public long getCreatetime() {
        return createtime;
    }

    public int getHashid() {
        return hashid;
    }

    public Request<C> hashid(int hashid) {
        this.hashid = hashid;
        return this;
    }

}
