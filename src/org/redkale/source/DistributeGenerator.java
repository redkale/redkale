/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * <pre>
 *     int   10万-100万     (36进制 4位)  255t - lflr    长度4  rewrite "^/dir/(\w+)/((\w{2})(\w{2})\..*)$" /$1/$3/$2 last;
 *     int  1000万-6000万   (36进制 5位)  5yc1t - zq0an   长度5-6   rewrite "^/dir/(\w+)/((\w{2})(\w{2})(\w\w?)\..*)$" /$1/$3/$4/$2 last;
 *     int    2亿-20亿      (36进制 6位)  3b2ozl - x2qxvk
 *    long   30亿-770亿     (36进制 7位)  1dm4etd - zdft88v   长度7-8   rewrite "^/dir/(\w+)/((\w{2})(\w{2})(\w{2})(\w\w?)\..*)$" /$1/$3/$4/$5/$2 last;
 *    long  1000亿-2万亿    (36进制 8位)  19xtf1tt - piscd0jj
 *    随机文件名:   (32进制 26位)   26-27长度
 *      #文件名 长度: 26 (1)
 *      rewrite "^/dir/(\w+)/((\w{2})(\w{2})(\w{2})(\w{2})(\w{2})(\w{2})(\w{14})\..*)$" /dir/$1/$3/$4/$5/$6/$7/$8/$2;
 *      #文件名 长度: 26 (2)
 *      rewrite "^/dir/(\w+)/(\w\w/\w\w/\w\w/\w\w/\w\w/\w\w)/(\w{12}(\w{2})(\w{2})(\w{2})(\w{2})(\w{2})(\w{2})(\w{2})\..*)$" /$1/$2/$4/$5/$6/$7/$8/$9/$3 last;
 *
 * </pre>
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface DistributeGenerator {

    long initialValue() default 1;

    /**
     * 如果allocationSize的值小于或等于1,则主键不会加上nodeid
     *
     * @return allocationSize
     */
    int allocationSize() default 1000;
}
