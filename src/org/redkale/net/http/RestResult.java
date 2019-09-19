/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.convert.Convert;

/**
 * 当RestMapping方法需要指定Convert进行序列化时将结果和Convert对象绑定输出
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 结果对象的类型
 */
public class RestResult<T> {

    protected Convert convert;

    protected T result;

    public RestResult() {
    }

    public RestResult(Convert convert, T result) {
        this.convert = convert;
        this.result = result;
    }

    public Convert getConvert() {
        return convert;
    }

    public void setConvert(Convert convert) {
        this.convert = convert;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

}
