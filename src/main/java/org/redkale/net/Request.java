/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Creator;

/**
 * 协议请求对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <C> Context子类型
 */
public abstract class Request<C extends Context> {

    protected final C context;

    protected final BsonConvert bsonConvert;

    protected final JsonConvert jsonConvert;

    protected long createTime;

    protected boolean keepAlive;

    // 请求包是否完成读取完毕，用于ProtocolCodec继续读的判断条件
    // 需要在readHeader方法中设置
    protected boolean completed;

    protected int pipelineIndex;

    protected int pipelineCount;

    protected boolean pipelineCompleted;

    protected String traceid;

    // Service动态生成接口的方法上的注解
    protected Annotation[] annotations;

    protected AsyncConnection channel;

    /** properties 与 attributes 的区别在于：调用recycle时， attributes会被清空而properties会保留; properties 通常存放需要永久绑定在request里的一些对象 */
    private final Map<String, Object> properties = new HashMap<>();

    /** 每次新请求都会清空 */
    protected final Map<String, Object> attributes = new HashMap<>();

    protected Request(C context) {
        this.context = context;
        this.bsonConvert = context.getBsonConvert();
        this.jsonConvert = context.getJsonConvert();
    }

    protected Request(Request<C> request) {
        this.context = request.context;
        this.bsonConvert = request.bsonConvert;
        this.jsonConvert = request.jsonConvert;
        this.createTime = request.createTime;
        this.keepAlive = request.keepAlive;
        this.pipelineIndex = request.pipelineIndex;
        this.pipelineCount = request.pipelineCount;
        this.pipelineCompleted = request.pipelineCompleted;
        this.traceid = request.traceid;
        this.annotations = request.annotations;
        this.channel = request.channel;
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
     * @param last 同一Channel的上一个Request
     * @return 缺少的字节数
     */
    protected abstract int readHeader(ByteBuffer buffer, Request last);

    protected abstract Serializable getRequestid();

    protected abstract void prepare();

    protected void recycle() {
        traceid = null;
        createTime = 0;
        pipelineIndex = 0;
        pipelineCount = 0;
        pipelineCompleted = false;
        completed = false;
        keepAlive = false;
        attributes.clear();
        annotations = null;
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
        return ((AsyncNioConnection) channel).newInputStream();
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

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public C getContext() {
        return this.context;
    }

    public long getCreateTime() {
        return createTime;
    }

    public Annotation[] getAnnotations() {
        if (annotations == null) {
            return new Annotation[0];
        }
        return Arrays.copyOfRange(annotations, 0, annotations.length);
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotations != null) {
            for (Annotation ann : annotations) {
                if (ann.annotationType() == annotationClass) {
                    return annotationClass.cast(ann);
                }
            }
        }
        return null;
    }

    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        if (annotations == null) {
            return (T[]) Array.newInstance(annotationClass, 0);
        } else {
            List<T> list = new ArrayList<>();
            for (Annotation ann : annotations) {
                if (ann.annotationType() == annotationClass) {
                    list.add(annotationClass.cast(ann));
                }
            }
            return list.toArray(Creator.funcArray(annotationClass));
        }
    }

    /**
     * @see #getCreateTime()
     * @return long
     * @deprecated replace by {@link #getCreateTime() }
     */
    @Deprecated(since = "2.7.0")
    @ConvertDisabled
    public long getCreatetime() {
        return getCreateTime();
    }

    public String getTraceid() {
        return traceid;
    }

    public Request<C> traceid(String traceid) {
        this.traceid = traceid;
        return this;
    }
}
