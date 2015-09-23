/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service.weixin;

import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import static com.wentch.redkale.util.Utility.getHttpContent;
import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import javax.annotation.*;

/**
 * 微信服务号Service
 *
 * @author zhangjx
 */
public class WeiXinMPService implements Service {

    protected static final Type MAPTYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final boolean finest = logger.isLoggable(Level.FINEST);

    private final boolean finer = logger.isLoggable(Level.FINER);

    protected final Map<String, String> mpsecrets = new HashMap<>();

    @Resource
    protected JsonConvert convert;

    // http://m.xxx.com/pipes/wx/verifymp
    @Resource(name = "property.wxmp.token")
    protected String mptoken = "";

    @Resource(name = "property.wxmp.corpid")
    protected String mpcorpid = "wxYYYYYYYYYYYYYY";

    @Resource(name = "property.wxmp.aeskey")
    protected String mpaeskey = "";

    public WeiXinMPService() {
        // mpsecrets.put("wxYYYYYYYYYYYYYYYYYY", "xxxxxxxxxxxxxxxxxxxxxxxxxxx"); 
    }

    //-----------------------------------微信服务号接口----------------------------------------------------------
    public RetResult<String> getMPWxunionidByCode(String appid, String code) {
        try {
            Map<String, String> wxmap = getMPUserTokenByCode(appid, code);
            final String unionid = wxmap.get("unionid");
            if (unionid != null && !unionid.isEmpty()) return new RetResult<>(unionid);
            return new RetResult<>(1011002);
        } catch (IOException e) {
            return new RetResult<>(1011001);
        }
    }

    public Map<String, String> getMPUserTokenByCode(String appid, String code) throws IOException {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + appid + "&secret=" + mpsecrets.get(appid) + "&code=" + code + "&grant_type=authorization_code";
        String json = getHttpContent(url);
        if (finest) logger.finest(url + "--->" + json);
        Map<String, String> jsonmap = convert.convertFrom(MAPTYPE, json);
        return getMPUserTokenByOpenid(jsonmap.get("access_token"), jsonmap.get("openid"));
    }

    public Map<String, String> getMPUserTokenByOpenid(String access_token, String openid) throws IOException {
        String url = "https://api.weixin.qq.com/sns/userinfo?access_token=" + access_token + "&openid=" + openid;
        String json = getHttpContent(url);
        if (finest) logger.finest(url + "--->" + json);
        Map<String, String> jsonmap = convert.convertFrom(MAPTYPE, json.replaceFirst("\\[.*\\]", "null"));
        return jsonmap;
    }

    public String verifyMPURL(String msgSignature, String timeStamp, String nonce, String echoStr) {
        String signature = sha1(mptoken, timeStamp, nonce);
        if (!signature.equals(msgSignature)) throw new RuntimeException("signature verification error");
        return echoStr;
    }

    /**
     * 用SHA1算法生成安全签名
     * <p>
     * @param strings
     * @return 安全签名
     */
    protected static String sha1(String... strings) {
        try {
            Arrays.sort(strings);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (String s : strings) md.update(s.getBytes());
            return Utility.binToHexString(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("SHA encryption to generate signature failure", e);
        }
    }
}
