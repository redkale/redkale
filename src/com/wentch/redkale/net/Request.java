/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class Request {

    protected final Context context;

    protected long createtime;

    protected boolean keepAlive;

    protected AsyncConnection channel;

    /**
     * properties 与 attributes 的区别在于：调用recycle时， attributes会被清空而properties会保留;
     * properties 通常存放需要永久绑定在request里的一些对象
     */
    private final Map<String, Object> properties = new HashMap<>();

    protected final Map<String, Object> attributes = new HashMap<>();

    protected Request(Context context) {
        this.context = context;
    }

    /**
     * 返回值：Integer.MIN_VALUE: 帧数据； -1：数据不合法； 0：解析完毕； >0: 需再读取的字节数。
     *
     * @param buffer
     * @return
     */
    protected abstract int readHeader(ByteBuffer buffer);

    protected abstract void readBody(ByteBuffer buffer);

    protected abstract void prepare();

    protected void recycle() {
        createtime = 0;
        keepAlive = false;
        attributes.clear();
        channel = null; //   close it by  response
    }

    protected void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getProperty(String name) {
        return (T) properties.get(name);
    }

    protected void removeProperty(String name) {
        properties.remove(name);
    }

    protected Map<String, Object> getProperties() {
        return properties;
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Context getContext() {
        return this.context;
    }
}
