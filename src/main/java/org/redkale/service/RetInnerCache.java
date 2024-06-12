/*

*/

package org.redkale.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import static org.redkale.boot.Application.SYSNAME_APP_CONF_DIR;
import static org.redkale.boot.Application.SYSNAME_APP_HOME;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
class RetInnerCache {

    static final ReentrantLock loadLock = new ReentrantLock();

    static Map<String, Map<Integer, String>> allRets = new LinkedHashMap<>();

    static Map<Integer, String> defRets = new LinkedHashMap<>();

    private RetInnerCache() {}

    static Map<String, Map<Integer, String>> loadMap(Class clazz) {
        final Map<String, Map<Integer, String>> allRetMap = new LinkedHashMap<>();
        ServiceLoader<RetInfoTransfer> loader = ServiceLoader.load(RetInfoTransfer.class);
        RedkaleClassLoader.putServiceLoader(RetInfoTransfer.class);
        Iterator<RetInfoTransfer> it = loader.iterator();
        RetInfoTransfer func = it.hasNext() ? it.next() : null;
        if (func != null) {
            RedkaleClassLoader.putReflectionPublicConstructors(
                    func.getClass(), func.getClass().getName());
        }
        RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
        for (Field field : clazz.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != int.class) {
                continue;
            }
            RetLabel[] infos = field.getAnnotationsByType(RetLabel.class);
            if (infos == null || infos.length == 0) {
                continue;
            }
            int value;
            try {
                value = field.getInt(null);
            } catch (Exception ex) {
                ex.printStackTrace();
                continue;
            }
            for (RetLabel info : infos) {
                allRetMap
                        .computeIfAbsent(info.locale(), k -> new LinkedHashMap<>())
                        .put(value, func == null ? info.value() : func.apply(value, info.value()));
            }
        }
        try {
            File homePath = new File(System.getProperty(SYSNAME_APP_HOME, ""), "conf");
            File propPath = new File(System.getProperty(SYSNAME_APP_CONF_DIR, homePath.getPath()));
            if (propPath.isDirectory() && propPath.canRead()) {
                final String prefix = clazz.getSimpleName().toLowerCase();
                for (File propFile : propPath.listFiles(
                        f -> f.getName().startsWith(prefix) && f.getName().endsWith(".properties"))) {
                    if (propFile.isFile() && propFile.canRead()) {
                        String locale =
                                propFile.getName().substring(prefix.length()).replaceAll("\\.\\d+", "");
                        locale = locale.substring(0, locale.indexOf(".properties"));
                        Map<Integer, String> defRetMap = allRetMap.get(locale);
                        if (defRetMap != null) {
                            InputStreamReader in =
                                    new InputStreamReader(new FileInputStream(propFile), StandardCharsets.UTF_8);
                            Properties prop = new Properties();
                            prop.load(in);
                            in.close();
                            prop.forEach((k, v) -> {
                                int retcode = Integer.parseInt(k.toString());
                                if (defRetMap.containsKey(retcode)) {
                                    defRetMap.put(retcode, v.toString());
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            // do nothing
        }
        return allRetMap;
    }
}
