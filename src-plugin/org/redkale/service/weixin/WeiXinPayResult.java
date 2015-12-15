/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.weixin;

import org.redkale.service.RetResult;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class WeiXinPayResult extends RetResult<String> {

    //待支付
    public static final short PAYSTATUS_UNPAY = 10;

    //已支付
    public static final short PAYSTATUS_PAYOK = 30;

    private long orderid;

    private long payid;

    private long payedmoney;

    private short paystatus;

    public WeiXinPayResult() {
    }

    public WeiXinPayResult(int retcode) {
        super(retcode);
    }

    public WeiXinPayResult(long orderid, long payid, short paystatus, long payedmoney, String resultcontent) {
        this.orderid = orderid;
        this.payid = payid;
        this.paystatus = paystatus;
        this.payedmoney = payedmoney;
        this.setResult(resultcontent);
    }

    public long getOrderid() {
        return orderid;
    }

    public void setOrderid(long orderid) {
        this.orderid = orderid;
    }

    public long getPayid() {
        return payid;
    }

    public void setPayid(long payid) {
        this.payid = payid;
    }

    public long getPayedmoney() {
        return payedmoney;
    }

    public void setPayedmoney(long payedmoney) {
        this.payedmoney = payedmoney;
    }

    public short getPaystatus() {
        return paystatus;
    }

    public void setPaystatus(short paystatus) {
        this.paystatus = paystatus;
    }

}
