/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.convert.json.*;

/**
 * 通用的结果对象
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

    public boolean isSuccess() {
        return retcode == 0;
    }

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
