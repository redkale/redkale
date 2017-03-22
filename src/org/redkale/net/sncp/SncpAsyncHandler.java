/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.util.logging.Level;
import org.redkale.convert.bson.*;
import org.redkale.net.sncp.SncpDynServlet.SncpServletAction;
import org.redkale.util.AsyncHandler;

/**
 * 异步回调函数  <br>
 *
 * public class _DyncSncpAsyncHandler extends XXXAsyncHandler implements SncpAsyncHandler
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <V> 结果对象的泛型
 * @param <A> 附件对象的泛型
 */
public interface SncpAsyncHandler<V, A> extends AsyncHandler<V, A> {

    public Object[] sncp_getParams();

    public void sncp_setParams(Object... params);

    static class Factory {

        /**
         * <blockquote><pre>
         * 若参数类型为AsyncHandler子类，必须保证其子类可被继承且completed、failed可被重载且包含空参数的构造函数。
         * 考虑点：
         *      1、AsyncHandler子类是接口，且还有其他多个方法
         *      2、AsyncHandler子类是类， 需要继承，且必须有空参数构造函数
         *      3、AsyncHandler子类无论是接口还是类，都可能存在其他泛型
         *
         *  public class _DyncSncpAsyncHandler_xxx extends XXXAsyncHandler implements SncpAsyncHandler {
         *
         *      public SncpAsyncHandler handler;
         *
         *      protected Object[] params;
         *
         *      &#64;Override
         *      public void completed(Object result, Object attachment) {
         *          handler.completed(result, attachment);
         *      }
         *
         *      &#64;Override
         *      public void failed(Throwable exc, Object attachment) {
         *          handler.failed(exc, attachment);
         *      }
         *
         *      &#64;Override
         *      public Object[] sncp_getParams() {
         *          return params;
         *      }
         *
         *      &#64;Override
         *      public void sncp_setParams(Object... params) {
         *          this.params = params;
         *          handler.sncp_setParams(params);
         *      }
         *  }
         *
         * </pre></blockquote>
         *
         * @param <V>          结果对象的泛型
         * @param <A>          附件对象的泛型
         * @param handlerClass AsyncHandler类型或子类
         * @param action       SncpServletAction
         * @param in           BsonReader
         * @param out          BsonWriter
         * @param request      SncpRequest
         * @param response     SncpResponse
         *
         * @return SncpAsyncHandler
         */
        public static <V, A> SncpAsyncHandler<V, A> create(Class<? extends AsyncHandler> handlerClass, SncpServletAction action,
            BsonReader in, BsonWriter out, SncpRequest request, SncpResponse response) {
            if (handlerClass == AsyncHandler.class) return new DefaultSncpAsyncHandler(action, in, out, request, response);
            //子类,  待实现
            return new DefaultSncpAsyncHandler(action, in, out, request, response);
        }
    }

    static class DefaultSncpAsyncHandler<V, A> implements SncpAsyncHandler<V, A> {

        //为了在回调函数中调用_callParameter方法
        protected Object[] params;

        protected SncpServletAction action;

        protected BsonReader in;

        protected BsonWriter out;

        protected SncpRequest request;

        protected SncpResponse response;

        public DefaultSncpAsyncHandler(SncpServletAction action, BsonReader in, BsonWriter out, SncpRequest request, SncpResponse response) {
            this.action = action;
            this.in = in;
            this.out = out;
            this.request = request;
            this.response = response;
        }

        @Override
        public void completed(Object result, Object attachment) {
            try {
                action._callParameter(out, sncp_getParams());
                action.convert.convertTo(out, Object.class, result);
                response.finish(0, out);
            } catch (Exception e) {
                failed(e, attachment);
            } finally {
                action.convert.offerBsonReader(in);
                action.convert.offerBsonWriter(out);
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            response.getContext().getLogger().log(Level.INFO, "sncp execute error(" + request + ")", exc);
            response.finish(SncpResponse.RETCODE_THROWEXCEPTION, null);
        }

        @Override
        public Object[] sncp_getParams() {
            return params;
        }

        @Override
        public void sncp_setParams(Object... params) {
            this.params = params;
        }

    }
}
