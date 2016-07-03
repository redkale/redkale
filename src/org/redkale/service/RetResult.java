/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import org.redkale.convert.json.JsonFactory;

/**
 * 通用的结果对象，在常见的HTTP+JSON接口中返回的结果需要含结果码，错误信息，和实体对象。
 * 通常前四位为模块，后四位为操作。
 * 结果码定义范围:
 *    // 10000001 - 19999999 预留给Redkale的核心包使用
 *    // 20000001 - 29999999 预留给Redkale的扩展包使用
 *    // 30000001 - 99999999 预留给Dev开发系统自身使用
 *
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 * @param <T> 结果对象的泛型
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

    protected T result;

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
     * @return 是否成功
     */
    public boolean isSuccess() {
        return retcode == 0;
    }

    /**
     * 结果码 0表示成功、 非0表示错误
     *
     * @return 结果码
     */
    public int getRetcode() {
        return retcode;
    }

    public void setRetcode(int retcode) {
        this.retcode = retcode;
    }

    /**
     * 同 setRetcode
     *
     * @param retcode retcode
     *
     * @return RetResult
     */
    public RetResult<T> retcode(int retcode) {
        this.retcode = retcode;
        return this;
    }

    public String getRetinfo() {
        return retinfo;
    }

    public void setRetinfo(String retinfo) {
        this.retinfo = retinfo;
    }

    /**
     * 同 setRetinfo
     *
     * @param retinfo retinfo
     *
     * @return RetResult
     */
    public RetResult<T> retinfo(String retinfo) {
        this.retinfo = retinfo;
        return this;
    }

    /**
     * 结果对象， 通常只有在retcode = 0时值才有效
     *
     * @return 结果对象
     */
    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    /**
     * 同 setResult
     *
     * @param result result
     *
     * @return RetResult
     */
    public RetResult<T> result(T result) {
        this.result = result;
        return this;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
