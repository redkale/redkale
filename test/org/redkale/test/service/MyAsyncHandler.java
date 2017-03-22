/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import org.redkale.util.AsyncHandler;

/**
 *
 * @author zhangjx
 * @param <V> V
 * @param <A> A
 */
public abstract class MyAsyncHandler<V, A> implements AsyncHandler<V, A> {

    public abstract int id();

}
