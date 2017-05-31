/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.reflect.Method;
import java.net.*;

/**
 *
 * @author zhangjx
 */
public class NodeClassLoader extends URLClassLoader {

    private static final Method addURLMethod;

    static {
        Method m = null;
        try {
            m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            m.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        addURLMethod = m;
    }

    public NodeClassLoader(URLClassLoader parent) {
        super(new URL[0], parent);
    }

    public Class<?> loadClass(String name, byte[] b) {
        return defineClass(name, b, 0, b.length);
    }

    @Override
    public void addURL(URL url) {
        try {
            addURLMethod.invoke(getParent(), url);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URL[] getURLs() {
        return ((URLClassLoader) getParent()).getURLs();
    }

}
