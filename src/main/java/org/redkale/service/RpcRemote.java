/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 用于在 Service 中创建自身远程模式的对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface RpcRemote {}
