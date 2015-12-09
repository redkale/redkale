/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import org.redkale.convert.json.*;

/**
 * 通用的结果对象，在常见的HTTP+JSON接口中返回的结果需要含结果码，错误信息，和实体对象。
 *
 * @author zhangjx
 * @param <T>
 */
public class RetResult<T> {

    protected static final class RetSuccessResult<T> extends RetResult<T> {

        public RetSuccessResult() {
        }

        @Override
        public void setRetcode(int retcode) {
        }

        @Override
        public void setRetinfo(String retinfo) {
        }

        @Override
        public void setResult(T result) {
        }
    }

    public static final RetResult SUCCESS = new RetSuccessResult();

    protected int retcode;

    protected String retinfo;

    private T result;

    public RetResult() {
    }

    public RetResult(T result) {
        this.result = result;
    }

    public RetResult(int retcode) {
        this.retcode = retcode;
    }

    public RetResult(int retcode, String retinfo) {
        this.retcode = retcode;
        this.retinfo = retinfo;
    }

    public RetResult(int retcode, String retinfo, T result) {
        this.retcode = retcode;
        this.retinfo = retinfo;
        this.result = result;
    }

    /**
     * 判断结果是否成功返回， retcode = 0 视为成功， 否则视为错误码
     *
     * @return
     */
    public boolean isSuccess() {
        return retcode == 0;
    }

    /**
     * 结果码 0表示成功、 非0表示错误
     *
     * @return
     */
    public int getRetcode() {
        return retcode;
    }

    public void setRetcode(int retcode) {
        this.retcode = retcode;
    }

    public String getRetinfo() {
        return retinfo;
    }

    public void setRetinfo(String retinfo) {
        this.retinfo = retinfo;
    }

    /**
     * 结果对象， 通常只有在retcode = 0时值才有效
     *
     * @return
     */
    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
