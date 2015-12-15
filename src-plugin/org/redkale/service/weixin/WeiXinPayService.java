/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.weixin;

import org.redkale.util.Utility;
import org.redkale.convert.json.JsonConvert;
import org.redkale.service.RetResult;
import org.redkale.util.AutoLoad;
import org.redkale.service.LocalService;
import org.redkale.service.Service;
import static org.redkale.service.weixin.WeiXinPayResult.*;
import java.security.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;
import javax.annotation.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@AutoLoad(false)
@LocalService
public class WeiXinPayService implements Service {

    private static final DateFormat FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final Pattern PAYXML = Pattern.compile("<([^/>]+)>(.+)</.+>"); // "<([^/>]+)><!\\[CDATA\\[(.+)\\]\\]></.+>"

    public static final int PAY_WX_ERROR = 4012101;//微信支付失败

    public static final int PAY_FALSIFY_ORDER = 4012017;//交易签名被篡改

    public static final int PAY_STATUS_ERROR = 4012018;//订单或者支付状态不正确

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean fine = logger.isLoggable(Level.FINE);

    protected final boolean finer = logger.isLoggable(Level.FINER);

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    @Resource(name = "property.wxpay.appid") //公众账号ID
    protected String wxpayappid = "wxYYYYYYYYYYYY";

    @Resource(name = "property.wxpay.mchid") //商户ID
    protected String wxpaymchid = "xxxxxxxxxxx";

    @Resource(name = "property.wxpay.sdbmchid") //子商户ID，受理模式必填
    protected String wxpaysdbmchid = "";

    @Resource(name = "property.wxpay.key") //签名算法需要用到的秘钥
    protected String wxpaykey = "##########################";

    @Resource(name = "property.wxpay.certpwd")
    protected String wxpaycertpwd = "xxxxxxxxxx"; //HTTP证书的密码，默认等于MCHID

    @Resource(name = "property.wxpay.certpath") //HTTP证书在服务器中的路径，用来加载证书用
    protected String wxpaycertpath = "apiclient_cert.p12";

    @Resource
    protected JsonConvert convert;

    /**
     * <xml><return_code><![CDATA[SUCCESS]]></return_code>
     * + "<return_msg><![CDATA[OK]]></return_msg>
     * + "<appid><![CDATA[wx4ad12c89818dd981]]></appid>
     * + "<mch_id><![CDATA[1241384602]]></mch_id>
     * + "<nonce_str><![CDATA[RpGucJ6wKtPgpTJy]]></nonce_str>
     * + "<sign><![CDATA[DFD99D5DA7DCA4FB5FB79ECAD49B9369]]></sign>
     * + "<result_code><![CDATA[SUCCESS]]></result_code>
     * + "<prepay_id><![CDATA[wx2015051518135700aaea6bc30284682518]]></prepay_id>
     * + "<trade_type><![CDATA[JSAPI]]></trade_type>
     * + "</xml>
     * <p>
     * @param orderid
     * @param payid
     * @param orderpayid
     * @param paymoney
     * @param clientAddr
     * @param notifyurl
     * @param map
     * @return
     */
    public RetResult<Map<String, String>> paying(long orderid, long payid, long orderpayid, long paymoney, String clientAddr, String notifyurl, Map<String, String> map) {
        RetResult result = null;
        try {
            if (!(map instanceof SortedMap)) map = new TreeMap<>(map);
            map.put("appid", wxpayappid);
            map.put("mch_id", wxpaymchid);
            map.put("nonce_str", Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime()));
            map.putIfAbsent("body", "服务");
            map.put("attach", "" + payid);
            map.put("out_trade_no", "" + orderpayid);
            map.put("total_fee", "" + paymoney);
            map.put("spbill_create_ip", clientAddr);
            synchronized (FORMAT) {
                map.put("time_expire", FORMAT.format(new Date(System.currentTimeMillis() + 10 * 60 * 60 * 1000)));
            }
            map.put("notify_url", notifyurl);
            {
                final StringBuilder sb = new StringBuilder();
                map.forEach((x, y) -> sb.append(x).append('=').append(y).append('&'));
                sb.append("key=").append(wxpaykey);
                map.put("sign", Utility.binToHexString(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes())).toUpperCase());
            }
            if (finest) logger.finest("weixinpaying2: " + orderid + " -> unifiedorder.map =" + map);
            Map<String, String> wxresult = formatXMLToMap(Utility.postHttpContent("https://api.mch.weixin.qq.com/pay/unifiedorder", formatMapToXML(map)));
            if (finest) logger.finest("weixinpaying3: " + orderid + " -> unifiedorder.callback =" + wxresult);
            if (!"SUCCESS".equals(wxresult.get("return_code"))) return new RetResult<>(PAY_WX_ERROR);
            if (!checkSign(wxresult)) return new RetResult(PAY_FALSIFY_ORDER);
            /**
             * "appId" : "wx2421b1c4370ec43b", //公众号名称，由商户传入 "timeStamp":" 1395712654", //时间戳，自1970年以来的秒数 "nonceStr" : "e61463f8efa94090b1f366cccfbbb444", //随机串 "package" :
             * "prepay_id=u802345jgfjsdfgsdg888", "signType" : "MD5", //微信签名方式: "paySign" : "70EA570631E4BB79628FBCA90534C63FF7FADD89" //微信签名
             */
            Map<String, String> rs = new TreeMap<>();
            rs.put("appId", this.wxpayappid);
            rs.put("timeStamp", Long.toString(System.currentTimeMillis() / 1000));
            rs.put("nonceStr", Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime()));
            rs.put("package", "prepay_id=" + wxresult.get("prepay_id"));
            rs.put("signType", "MD5");
            {
                final StringBuilder sb2 = new StringBuilder();
                rs.forEach((x, y) -> sb2.append(x).append('=').append(y).append('&'));
                sb2.append("key=").append(wxpaykey);
                rs.put("paySign", Utility.binToHexString(MessageDigest.getInstance("MD5").digest(sb2.toString().getBytes())).toUpperCase());
            }
            if (finest) logger.finest("weixinpaying4: " + orderid + " -> unifiedorder.result =" + rs);
            RetResult rr = new RetResult(rs);
            rr.setRetinfo("" + orderpayid);
            return rr;
        } catch (Exception e) {
            logger.log(Level.WARNING, "paying error.", e);
        }
        return result;
    }

    public RetResult closepay(long orderpayid) {
        RetResult result = null;
        try {
            Map<String, String> map = new TreeMap<>();
            map.put("appid", wxpayappid);
            map.put("mch_id", wxpaymchid);
            map.put("nonce_str", Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime()));
            map.put("out_trade_no", "" + orderpayid);
            {
                final StringBuilder sb = new StringBuilder();
                map.forEach((x, y) -> sb.append(x).append('=').append(y).append('&'));
                sb.append("key=").append(wxpaykey);
                map.put("sign", Utility.binToHexString(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes())).toUpperCase());
            }
            if (finest) logger.finest("weixinclosepay2: " + orderpayid + " -> closeorder.map =" + map);
            Map<String, String> wxresult = formatXMLToMap(Utility.postHttpContent("https://api.mch.weixin.qq.com/pay/closeorder", formatMapToXML(map)));
            if (finest) logger.finest("weixinclosepay3: " + orderpayid + " -> closeorder.callback =" + wxresult);
            if (!"SUCCESS".equals(wxresult.get("return_code"))) return new RetResult<>(PAY_WX_ERROR);
            if (!checkSign(wxresult)) return new RetResult(PAY_FALSIFY_ORDER);
            return new RetResult(wxresult);
        } catch (Exception e) {
            logger.log(Level.WARNING, "closepay error: " + orderpayid, e);
        }
        return result;
    }

    public WeiXinPayResult checkPay(long orderid, long orderpayid) {
        WeiXinPayResult result = new WeiXinPayResult(PAY_STATUS_ERROR);
        try {
            Map<String, String> map = new TreeMap<>();
            map.put("appid", wxpayappid);
            map.put("mch_id", wxpaymchid);
            map.put("out_trade_no", "" + orderpayid);
            map.put("nonce_str", Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime()));
            {
                final StringBuilder sb = new StringBuilder();
                map.forEach((x, y) -> sb.append(x).append('=').append(y).append('&'));
                sb.append("key=").append(wxpaykey);
                map.put("sign", Utility.binToHexString(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes())).toUpperCase());
            }
            Map<String, String> wxresult = formatXMLToMap(Utility.postHttpContent("https://api.mch.weixin.qq.com/pay/orderquery", formatMapToXML(map)));
            return callbackPay(wxresult);
        } catch (Exception e) {
            logger.log(Level.FINER, "check weixinpay[" + orderid + "] except", e);
            return result;
        }
    }

    /**
     * <xml>
     * <appid><![CDATA[wx4ad12c89818dd981]]></appid>
     * <attach><![CDATA[10000070334]]></attach>
     * <bank_type><![CDATA[ICBC_DEBIT]]></bank_type>
     * <cash_fee><![CDATA[10]]></cash_fee>
     * <fee_type><![CDATA[CNY]]></fee_type>
     * <is_subscribe><![CDATA[Y]]></is_subscribe>
     * <mch_id><![CDATA[1241384602]]></mch_id>
     * <nonce_str><![CDATA[14d69ac6d6525f27dc9bcbebc]]></nonce_str>
     * <openid><![CDATA[ojEVbsyDUzGqlgX3eDgmAMaUDucA]]></openid>
     * <out_trade_no><![CDATA[1000072334]]></out_trade_no>
     * <result_code><![CDATA[SUCCESS]]></result_code>
     * <return_code><![CDATA[SUCCESS]]></return_code>
     * <sign><![CDATA[60D95E25EA9C4F54BD1020952303C4E2]]></sign>
     * <time_end><![CDATA[20150519085546]]></time_end>
     * <total_fee>10</total_fee>
     * <trade_type><![CDATA[JSAPI]]></trade_type>
     * <transaction_id><![CDATA[1009630061201505190139511926]]></transaction_id>
     * </xml>
     * <p>
     * @param map
     * @return
     */
    public WeiXinPayResult callbackPay(Map<String, String> map) {
        if (!"SUCCESS".equals(map.get("return_code"))) return new WeiXinPayResult(PAY_WX_ERROR);
        if (!(map instanceof SortedMap)) map = new TreeMap<>(map);
        if (!checkSign(map)) return new WeiXinPayResult(PAY_FALSIFY_ORDER);
        String state = map.get("trade_state");
        if (state == null && "SUCCESS".equals(map.get("result_code")) && Long.parseLong(map.get("total_fee")) > 0) {
            state = "SUCCESS";
        }
        short paystatus = "SUCCESS".equals(state) ? PAYSTATUS_PAYOK : PAYSTATUS_UNPAY;
        return new WeiXinPayResult(Long.parseLong(map.get("out_trade_no")), Long.parseLong(map.get("attach")), paystatus, Long.parseLong(map.get("total_fee")), convert.convertTo(map));
    }

    protected static String formatMapToXML(final Map<String, String> map) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<xml>");
        map.forEach((x, y) -> sb.append('<').append(x).append('>').append(y.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")).append("</").append(x).append('>'));
        sb.append("</xml>");
        return sb.toString();
    }

    protected boolean checkSign(Map<String, String> map) {
        if (!(map instanceof SortedMap)) map = new TreeMap<>(map);
        String sign = map.remove("sign");
        final StringBuilder sb = new StringBuilder();
        map.forEach((x, y) -> sb.append(x).append('=').append(y).append('&'));
        sb.append("key=").append(wxpaykey);
        try {
            return sign.equals(Utility.binToHexString(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes())).toUpperCase());
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, String> formatXMLToMap(final String xml) {
        Map<String, String> map = new TreeMap<>();
        Matcher m = PAYXML.matcher(xml.substring(xml.indexOf('>') + 1));
        while (m.find()) {
            String val = m.group(2);
            if (val.startsWith("<![CDATA[")) val = val.substring("<![CDATA[".length(), val.length() - 3);
            map.put(m.group(1), val);
        }
        return map;
    }

}
