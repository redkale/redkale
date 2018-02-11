/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import org.redkale.convert.json.*;
import org.redkale.net.http.*;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public interface HttpRequestDesc {

    //获取客户端地址IP
    public SocketAddress getRemoteAddress();

    //获取客户端地址IP, 与getRemoteAddres() 的区别在于：
    //本方法优先取header中指定为RemoteAddress名的值，没有则返回getRemoteAddres()的getHostAddress()。
    //本方法适用于服务前端有如nginx的代理服务器进行中转，通过getRemoteAddres()是获取不到客户端的真实IP。
    public String getRemoteAddr();

    //获取请求内容指定的编码字符串
    public String getBody(Charset charset);

    //获取请求内容的UTF-8编码字符串
    public String getBodyUTF8();

    //获取请求内容的byte[]
    public byte[] getBody();
    
    //获取请求内容的JavaBean对象
    public <T> T getBodyJson(java.lang.reflect.Type type);

    //获取请求内容的JavaBean对象
    public <T> T getBodyJson(JsonConvert convert, java.lang.reflect.Type type);
    
    //获取文件上传对象
    public MultiContext getMultiContext();

    //获取文件上传信息列表 等价于 getMultiContext().parts();
    public Iterable<MultiPart> multiParts() throws IOException;

    //设置当前用户信息, 通常在HttpServlet.preExecute方法里设置currentUser
    //数据类型由@HttpUserType指定
    public <T> HttpRequest setCurrentUser(T user);
    
    //获取当前用户信息 数据类型由@HttpUserType指定
    public <T> T currentUser();
    
    //获取模块ID，来自@HttpServlet.moduleid()
    public int getModuleid();
    
    //获取操作ID，来自@HttpMapping.actionid()
    public int getActionid();
    
    //获取sessionid
    public String getSessionid(boolean autoCreate);

    //更新sessionid
    public String changeSessionid();

    //指定值更新sessionid
    public String changeSessionid(String newsessionid);

    //使sessionid失效
    public void invalidateSession();

    //获取所有Cookie对象
    public java.net.HttpCookie[] getCookies();

    //获取Cookie值
    public String getCookie(String name);

    //获取Cookie值， 没有返回默认值
    public String getCookie(String name, String defaultValue);

    //获取协议名 http、https、ws、wss等
    public String getProtocol();

    //获取请求方法 GET、POST等
    public String getMethod();

    //获取Content-Type的header值
    public String getContentType();

    //获取请求内容的长度, 为-1表示内容长度不确定
    public long getContentLength();

    //获取Connection的Header值
    public String getConnection();

    //获取Host的Header值
    public String getHost();

    //获取请求的URL
    public String getRequestURI();

    //截取getRequestURI最后的一个/后面的部分
    public String getRequstURILastPath();

    // 获取请求URL最后的一个/后面的部分的short值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: short type = request.getRequstURILastPath((short)0); //type = 2
    public short getRequstURILastPath(short defvalue);

    // 获取请求URL最后的一个/后面的部分的short值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: short type = request.getRequstURILastPath((short)0); //type = 2
    public short getRequstURILastPath(int radix, short defvalue);

    // 获取请求URL最后的一个/后面的部分的int值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: int type = request.getRequstURILastPath(0); //type = 2
    public int getRequstURILastPath(int defvalue);

    // 获取请求URL最后的一个/后面的部分的int值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: int type = request.getRequstURILastPath(0); //type = 2
    public int getRequstURILastPath(int radix, int defvalue);

    // 获取请求URL最后的一个/后面的部分的float值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: float type = request.getRequstURILastPath(0.f); //type = 2.f
    public float getRequstURILastPath(float defvalue);

    // 获取请求URL最后的一个/后面的部分的long值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: long type = request.getRequstURILastPath(0L); //type = 2
    public long getRequstURILastPath(long defvalue);

    // 获取请求URL最后的一个/后面的部分的long值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: long type = request.getRequstURILastPath(0L); //type = 2
    public long getRequstURILastPath(int radix, long defvalue);

    // 获取请求URL最后的一个/后面的部分的double值   <br>
    // 例如请求URL /pipes/record/query/2   <br>
    // 获取type参数: double type = request.getRequstURILastPath(0.0); //type = 2.0
    public double getRequstURILastPath(double defvalue);

    //从prefix之后截取getRequestURI再对"/"进行分隔
    public String[] getRequstURIPaths(String prefix);

    // 获取请求URL分段中含prefix段的值
    // 例如请求URL /pipes/record/query/name:hello
    // 获取name参数: String name = request.getRequstURIPath("name:", "none");
    public String getRequstURIPath(String prefix, String defaultValue);

    // 获取请求URL分段中含prefix段的short值
    // 例如请求URL /pipes/record/query/type:10
    // 获取type参数: short type = request.getRequstURIPath("type:", (short)0);
    public short getRequstURIPath(String prefix, short defaultValue);

    // 获取请求URL分段中含prefix段的short值
    // 例如请求URL /pipes/record/query/type:a
    // 获取type参数: short type = request.getRequstURIPath(16, "type:", (short)0); type = 10
    public short getRequstURIPath(int radix, String prefix, short defvalue);

    // 获取请求URL分段中含prefix段的int值
    // 例如请求URL /pipes/record/query/offset:2/limit:50
    // 获取offset参数: int offset = request.getRequstURIPath("offset:", 1);
    // 获取limit参数: int limit = request.getRequstURIPath("limit:", 20);
    public int getRequstURIPath(String prefix, int defaultValue);

    // 获取请求URL分段中含prefix段的int值
    // 例如请求URL /pipes/record/query/offset:2/limit:10
    // 获取offset参数: int offset = request.getRequstURIPath("offset:", 1);
    // 获取limit参数: int limit = request.getRequstURIPath(16, "limit:", 20); // limit = 16
    public int getRequstURIPath(int radix, String prefix, int defaultValue);

    // 获取请求URL分段中含prefix段的float值   
    // 例如请求URL /pipes/record/query/point:40.0  
    // 获取time参数: float point = request.getRequstURIPath("point:", 0.0f);
    public float getRequstURIPath(String prefix, float defvalue);

    // 获取请求URL分段中含prefix段的long值
    // 例如请求URL /pipes/record/query/time:1453104341363/id:40
    // 获取time参数: long time = request.getRequstURIPath("time:", 0L);
    public long getRequstURIPath(String prefix, long defaultValue);

    // 获取请求URL分段中含prefix段的long值
    // 例如请求URL /pipes/record/query/time:1453104341363/id:40
    // 获取time参数: long time = request.getRequstURIPath("time:", 0L);
    public long getRequstURIPath(int radix, String prefix, long defvalue);

    // 获取请求URL分段中含prefix段的double值   <br>
    // 例如请求URL /pipes/record/query/point:40.0   <br>
    // 获取time参数: double point = request.getRequstURIPath("point:", 0.0);
    public double getRequstURIPath(String prefix, double defvalue);

    //获取所有的header名
    public AnyValue getHeaders();
    
    //将请求Header转换成Map
    public Map<String, String> getHeadersToMap(Map<String, String> map);
    
    //获取所有的header名
    public String[] getHeaderNames();

    // 获取指定的header值
    public String getHeader(String name);

    //获取指定的header值, 没有返回默认值
    public String getHeader(String name, String defaultValue);

    //获取指定的header的json值
    public <T> T getJsonHeader(Type type, String name);

    //获取指定的header的json值
    public <T> T getJsonHeader(JsonConvert convert, Type type, String name);

    //获取指定的header的boolean值, 没有返回默认boolean值
    public boolean getBooleanHeader(String name, boolean defaultValue);

    // 获取指定的header的short值, 没有返回默认short值
    public short getShortHeader(String name, short defaultValue);

    // 获取指定的header的short值, 没有返回默认short值
    public short getShortHeader(int radix, String name, short defaultValue);

    // 获取指定的header的short值, 没有返回默认short值
    public short getShortHeader(String name, int defaultValue);

    // 获取指定的header的short值, 没有返回默认short值
    public short getShortHeader(int radix, String name, int defaultValue);

    //获取指定的header的int值, 没有返回默认int值
    public int getIntHeader(String name, int defaultValue);

    //获取指定的header的int值, 没有返回默认int值
    public int getIntHeader(int radix, String name, int defaultValue);

    // 获取指定的header的long值, 没有返回默认long值
    public long getLongHeader(String name, long defaultValue);

    // 获取指定的header的long值, 没有返回默认long值
    public long getLongHeader(int radix, String name, long defaultValue);

    // 获取指定的header的float值, 没有返回默认float值
    public float getFloatHeader(String name, float defaultValue);

    //获取指定的header的double值, 没有返回默认double值
    public double getDoubleHeader(String name, double defaultValue);

    //获取请求参数总对象
    public AnyValue getParameters();
    
    //将请求参数转换成Map
    public Map<String, String> getParametersToMap(Map<String, String> map);
    
    //将请求参数转换成String, 字符串格式为: bean1={}&amp;id=13&amp;name=xxx
    //不会返回null，没有参数返回空字符串
    public String getParametersToString();
    
    //将请求参数转换成String, 字符串格式为: prefix + bean1={}&amp;id=13&amp;name=xxx
    //拼接前缀， 如果无参数，返回的字符串不会含有拼接前缀
    //不会返回null，没有参数返回空字符串
    public String getParametersToString(String prefix);
    
    //获取所有参数名
    public String[] getParameterNames();

    //获取指定的参数值
    public String getParameter(String name);

    //获取指定的参数值, 没有返回默认值
    public String getParameter(String name, String defaultValue);

    //获取指定的参数json值
    public <T> T getJsonParameter(Type type, String name);

    //获取指定的参数json值
    public <T> T getJsonParameter(JsonConvert convert, Type type, String name);

    //获取指定的参数boolean值, 没有返回默认boolean值
    public boolean getBooleanParameter(String name, boolean defaultValue);

    //获取指定的参数short值, 没有返回默认short值
    public short getShortParameter(String name, short defaultValue);

    //获取指定的参数short值, 没有返回默认short值
    public short getShortParameter(int radix, String name, short defaultValue);

    //获取指定的参数short值, 没有返回默认short值
    public short getShortParameter(int radix, String name, int defaultValue);

    //获取指定的参数int值, 没有返回默认int值
    public int getIntParameter(String name, int defaultValue);

    //获取指定的参数int值, 没有返回默认int值
    public int getIntParameter(int radix, String name, int defaultValue);

    //获取指定的参数long值, 没有返回默认long值
    public long getLongParameter(String name, long defaultValue);

    //获取指定的参数long值, 没有返回默认long值
    public long getLongParameter(int radix, String name, long defaultValue);

    //获取指定的参数float值, 没有返回默认float值
    public float getFloatParameter(String name, float defaultValue);

    //获取指定的参数double值, 没有返回默认double值
    public double getDoubleParameter(String name, double defaultValue);

    //获取翻页对象 同 getFlipper("flipper", false, 0);
    public org.redkale.source.Flipper getFlipper();

    //获取翻页对象 同 getFlipper("flipper", needcreate, 0);
    public org.redkale.source.Flipper getFlipper(boolean needcreate);

    //获取翻页对象 同 getFlipper("flipper", false, maxLimit);
    public org.redkale.source.Flipper getFlipper(int maxLimit);

    //获取翻页对象 同 getFlipper("flipper", needcreate, maxLimit)
    public org.redkale.source.Flipper getFlipper(boolean needcreate, int maxLimit);

    //获取翻页对象 http://redkale.org/pipes/records/list/offset:0/limit:20/sort:createtime%20ASC
    //http://redkale.org/pipes/records/list?flipper={'offset':0,'limit':20, 'sort':'createtime ASC'}
    //以上两种接口都可以获取到翻页对象
    public org.redkale.source.Flipper getFlipper(String name, boolean needcreate, int maxLimit);

    //获取HTTP上下文对象
    public HttpContext getContext();

    //获取所有属性值, servlet执行完后会被清空
    public Map<String, Object> getAttributes();

    //获取指定属性值, servlet执行完后会被清空
    public <T> T getAttribute(String name);

    //删除指定属性
    public void removeAttribute(String name);

    //设置属性值, servlet执行完后会被清空
    public void setAttribute(String name, Object value);

    //获取request创建时间
    public long getCreatetime();
}
