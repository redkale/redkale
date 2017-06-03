/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.net.*;
import java.util.HashSet;

/**
 *
 * @author zhangjx
 */
public class RedkaleClassLoader extends URLClassLoader {

    public RedkaleClassLoader(ClassLoader parent) {
        super(parentURL(parent), parent);
    }

    private static URL[] parentURL(ClassLoader parent) {
        ClassLoader loader = parent;
        HashSet<URL> set = new HashSet<>();
        do {
            String loaderName = loader.getClass().getName();
            if (loaderName.startsWith("sun.") && loaderName.contains("ExtClassLoader")) continue;
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    set.add(url);
                }
            }
        } while ((loader = loader.getParent()) != null);
        return set.toArray(new URL[set.size()]);
    }

    public Class<?> loadClass(String name, byte[] b) {
        return defineClass(name, b, 0, b.length);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public URL[] getURLs() {
        return super.getURLs();
    }

}
