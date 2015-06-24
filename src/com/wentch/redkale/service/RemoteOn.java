/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *  * @author zhangjx
 */
/*
 * 只能标识在Service类的方法上, 且Service类被实例成RemoteService时才有效。
 * 被@RemoteOn 标记的xxx方法必须存在onXxx方法， 且参数和返回值必须一致, onXxx方法必须声明为public final。 且onXxx方法不会被RemoteService重载。
 * 例如：
 * public class XXXService implements Service {
 *
 *      @Resource
 *      private HashMap<String, XXXService> nodemaps;
 *
 *      @RemoteOn
 *      public void send(XXXBean bean){
 *          nodemaps.forEach((x, y) -> {if(y != this) y.send(bean);});
 *      }
 *
 *      public final void onSend(XXXBean bean){
 *          ...
 *      }
 * }
 *
 * 如果没有public final void onSend(XXXBean bean)方法,生成RemoteService会抛出异常。
 */
@Inherited
@Documented
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface RemoteOn {

}
