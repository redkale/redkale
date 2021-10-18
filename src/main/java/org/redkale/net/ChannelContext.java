/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.*;

/**
 * 当前一个Request绑定的AsyncConnection， 类似Session，但概念上不同于sessionid对应的对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.4.0
 */
public interface ChannelContext {

    public void setAttribute(String name, Object value);

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name);

    public void removeAttribute(String name);

    public Map<String, Object> getAttributes();

    public void clearAttribute();
}
