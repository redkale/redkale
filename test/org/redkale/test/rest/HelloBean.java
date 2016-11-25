package org.redkale.test.rest;

import org.redkale.convert.json.JsonFactory;
import org.redkale.net.http.*;
import org.redkale.source.FilterBean;

public class HelloBean implements FilterBean {

    private int helloid;

    @RestHeader(name = "User-Agent")
    private String useragent; //从Http Header中获取浏览器信息
    
    @RestCookie(name = "hello-cookie")
    private String rescookie;  //从Cookie中获取名为hello-cookie的值

    @RestAddress
    private String clientaddr;  //客户端请求IP

    @RestSessionid
    private String sessionid;  //用户Sessionid, 未登录时为null

    /** 以下省略getter setter方法 */
    public int getHelloid() {
        return helloid;
    }

    public void setHelloid(int helloid) {
        this.helloid = helloid;
    }

    public String getUseragent() {
        return useragent;
    }

    public void setUseragent(String useragent) {
        this.useragent = useragent;
    }

    public String getRescookie() {
        return rescookie;
    }

    public void setRescookie(String rescookie) {
        this.rescookie = rescookie;
    }

    public String getClientaddr() {
        return clientaddr;
    }

    public void setClientaddr(String clientaddr) {
        this.clientaddr = clientaddr;
    }

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }

}
