/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import org.redkale.convert.json.*;

/**
 *
 * @author zhangjx
 */
public interface HttpResponseDesc {

    //设置状态码
    public void setStatus(int status);

    //获取状态码
    public int getStatus();

    //获取 ContentType
    public String getContentType();

    //设置 ContentType
    public void setContentType(String contentType);

    //获取内容长度
    public long getContentLength();

    //设置内容长度
    public void setContentLength(long contentLength);

    //设置Header值
    public void setHeader(String name, Object value);

    //添加Header值
    public void addHeader(String name, Object value);

    //跳过header的输出
    //通常应用场景是，调用者的输出内容里已经包含了HTTP的响应头信息，因此需要调用此方法避免重复输出HTTP响应头信息。
    public void skipHeader();

    //增加Cookie值
    public void addCookie(HttpCookie... cookies);

    //关闭HTTP连接，如果是keep-alive则不强制关闭
    public void finish();

    //强制关闭HTTP连接
    public void finish(boolean kill);

    //将对象以JSON格式输出
    public void finishJson(Object obj);

    //将对象以JSON格式输出
    public void finishJson(JsonConvert convert, Object obj);

    //将对象以JSON格式输出
    public void finishJson(Type type, Object obj);

    //将对象以JSON格式输出
    public void finishJson(final JsonConvert convert, final Type type, final Object obj);

    //将对象以JSON格式输出
    public void finishJson(final Object... objs);

    //将指定字符串以响应结果输出
    public void finish(String obj);

    //以指定响应码附带内容输出, message 可以为null
    public void finish(int status, String message);

    //以304状态码输出
    public void finish304();

    //以404状态码输出
    public void finish404();

    //将指定ByteBuffer按响应结果输出
    public void finish(ByteBuffer buffer);

    //将指定ByteBuffer按响应结果输出
    //kill   输出后是否强制关闭连接
    public void finish(boolean kill, ByteBuffer buffer);

    //将指定ByteBuffer数组按响应结果输出
    public void finish(ByteBuffer... buffers);

    //将指定ByteBuffer数组按响应结果输出
    //kill   输出后是否强制关闭连接
    public void finish(boolean kill, ByteBuffer... buffers);

    //将指定文件按响应结果输出
    public void finish(File file) throws IOException;

    //异步输出指定内容
    public <A> void sendBody(ByteBuffer buffer, A attachment, CompletionHandler<Integer, A> handler);
}
