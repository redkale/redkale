/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Column;
import org.redkale.util.*;

/**
 * 通用的结果对象，在常见的HTTP+JSON接口中返回的结果需要含结果码，错误信息，和实体对象。 <br>
 * 结果码定义通常前四位为模块，后四位为操作。<br>
 * 结果码定义范围: <br>
 * // 10000001 - 19999999 预留给Redkale的核心包使用 <br>
 * // 20000001 - 29999999 预留给Redkale的扩展包使用 <br>
 * // 30000001 - 99999999 预留给Dev开发系统自身使用 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 结果对象的泛型
 */
public class RetResult<T> implements Serializable {

    public static final Type TYPE_RET_INTEGER = new TypeToken<RetResult<Integer>>() {}.getType();

    public static final Type TYPE_RET_LONG = new TypeToken<RetResult<Long>>() {}.getType();

    public static final Type TYPE_RET_STRING = new TypeToken<RetResult<String>>() {}.getType();

    // @ConvertColumn(index = 1)
    // success
    //
    @ConvertColumn(index = 2)
    @Column(nullable = false)
    protected int retcode;

    @ConvertColumn(index = 3)
    protected String retinfo;

    @ConvertColumn(index = 4)
    protected T result;

    @ConvertDisabled
    protected Convert convert;

    public RetResult() {}

    public RetResult(T result) {
        this.result = result;
    }

    public RetResult(Convert convert, T result) {
        this.convert = convert;
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

    public Convert convert() {
        return convert;
    }

    public Convert clearConvert() {
        Convert c = this.convert;
        this.convert = null;
        return c;
    }

    public RetResult convert(Convert convert) {
        this.convert = convert;
        return this;
    }

    public <V> RetResult<V> cast(Type newType) {
        return cast(this, newType);
    }

    public static <V> RetResult<V> cast(RetResult rs, Type newType) {
        Object d = rs.result;
        if (d != null) {
            String text = d instanceof CharSequence
                    ? d.toString()
                    : JsonConvert.root().convertTo(d);
            V n = JsonConvert.root().convertFrom(newType, text);
            return new RetResult(rs.retcode, rs.retinfo, n).convert(rs.convert);
        }
        return rs;
    }

    public CompletableFuture<RetResult<T>> toFuture() {
        return CompletableFuture.completedFuture(this);
    }

    public CompletableFuture toAnyFuture() {
        return CompletableFuture.completedFuture(this);
    }

    public static RetResult success() {
        return new RetResult();
    }

    public static <T> RetResult<T> success(T result) {
        return new RetResult().result(result);
    }

    public static <T> CompletableFuture<RetResult<T>> successFuture() {
        return CompletableFuture.completedFuture(new RetResult());
    }

    public static <T> CompletableFuture<RetResult<T>> successFuture(T result) {
        return CompletableFuture.completedFuture(new RetResult(result));
    }

    // @since 2.7.0
    public static <T> RetResult<T> fail(int retcode, String retinfo) {
        return new RetResult(retcode, retinfo);
    }

    // @since 2.8.0
    public static <T> RetResult<T> fail(RetException ex) {
        return new RetResult(ex.getCode(), ex.getMessage());
    }

    // @since 2.7.0
    public static <T> CompletableFuture<RetResult<T>> failFuture(int retcode, String retinfo) {
        return CompletableFuture.completedFuture(new RetResult(retcode, retinfo));
    }

    // @since 2.8.0
    public static <T> CompletableFuture<RetResult<T>> failFuture(RetException ex) {
        return CompletableFuture.completedFuture(new RetResult(ex.getCode(), ex.getMessage()));
    }

    // @since 2.7.0
    public T join() {
        if (isSuccess()) {
            return result;
        }
        throw new RetcodeException(this.retcode, this.retinfo);
    }

    public static <T> RetResult<T> get(CompletableFuture<RetResult<T>> future, long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException ex) {
            throw new RedkaleException(ex.getCause());
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    public static <K, V> RetResult<Map<K, V>> map(Object... items) {
        return new RetResult(Utility.ofMap(items));
    }

    /**
     * 清空result
     *
     * @param <V> V
     * @return RetResult
     */
    public <V> RetResult<V> clearResult() {
        this.result = null;
        return (RetResult) this;
    }

    /**
     * 将RetResult&#60;X&#62; 转换成一个新的 RetResult&#60;Y&#62;
     *
     * @param <R> 目标数据类型
     * @param mapper 转换函数
     * @return RetResult
     * @since 2.1.0
     */
    public <R> RetResult<R> mapTo(Function<T, R> mapper) {
        return new RetResult<>(mapper.apply(this.result))
                .convert(this.convert)
                .retcode(this.retcode)
                .retinfo(this.retinfo);
    }

    /**
     * 同 setRetcode
     *
     * @param retcode retcode
     * @return RetResult
     */
    public RetResult<T> retcode(int retcode) {
        this.retcode = retcode;
        return this;
    }

    /**
     * 同 setRetinfo
     *
     * @param retinfo retinfo
     * @return RetResult
     */
    public RetResult<T> retinfo(String retinfo) {
        this.retinfo = retinfo;
        return this;
    }

    /**
     * 同 setResult
     *
     * @param result result
     * @return RetResult
     */
    public RetResult<T> result(T result) {
        this.result = result;
        return this;
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
     * retinfo值是否为空
     *
     * @return 是否为空
     * @since 2.7.0
     */
    public boolean emptyRetinfo() {
        return retinfo == null || retinfo.trim().isEmpty();
    }

    /**
     * 结果信息，通常retcode != 0时值为错误信息
     *
     * @return 结果信息
     */
    public String getRetinfo() {
        return retinfo;
    }

    /**
     * 设置结果信息
     *
     * @param retinfo 结果信息
     */
    public void setRetinfo(String retinfo) {
        this.retinfo = retinfo;
    }

    /**
     * 结果对象， 通常只有在retcode = 0时值才有效
     *
     * @return 结果对象
     */
    public T getResult() {
        return result;
    }

    /**
     * 设置结果对象
     *
     * @param result T
     */
    public void setResult(T result) {
        this.result = result;
    }

    /**
     * 判断结果是否成功返回， retcode = 0 视为成功， 否则视为错误码
     *
     * @return 是否成功
     */
    @ConvertColumn(index = 1)
    public boolean isSuccess() {
        return retcode == 0;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
