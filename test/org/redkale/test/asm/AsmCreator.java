/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.asm;

import java.io.*;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class AsmCreator {

    public static void main(String[] args) throws Throwable {
        boolean realasm = false; //从http://forge.ow2.org/projects/asm/ 下载最新asm的src放在 srcasmroot 目录下
        File srcasmroot = new File("D:/JAVA/JDK源码/jdk/internal/org/objectweb/asm");
        if (realasm) srcasmroot = new File("D:/JAVA/JDK源码/org/objectweb/asm");
        File destasmroot = new File("D:/Java-Projects/RedkaleProject/src/org/redkale/asm");
        String line = null;
        LineNumberReader txtin = new LineNumberReader(new FileReader(new File(destasmroot, "asm.txt")));
        while ((line = txtin.readLine()) != null) {
            line = line.trim();
            if (!line.endsWith(".java")) continue;
            File srcfile = new File(srcasmroot, line);
            if (!srcfile.isFile()) continue;
            File destfile = new File(destasmroot, line);
            String content = Utility.readThenClose(new FileInputStream(srcfile));
            FileOutputStream out = new FileOutputStream(destfile);
            out.write(content.replace("jdk.internal.org.objectweb", "org.redkale").replace("org.objectweb", "org.redkale").getBytes());
            out.close();
        }
        //需要屏蔽ClassReader中判断checks the class version的部分
    }
}
