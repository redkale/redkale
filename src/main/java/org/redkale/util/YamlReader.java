/*

*/

package org.redkale.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.redkale.annotation.Nonnull;
import org.redkale.util.YamlProvider.YamlLoader;

/**
 * 简单的yml读取器
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.8.0
 * @author zhangjx
 */
public class YamlReader {

    private static YamlLoader currentYamlLoader;

    private final String text;

    public YamlReader(String text) {
        this.text = Objects.requireNonNull(text);
    }

    public AnyValue read() {
        return loadYamlLoader().read(text);
    }

    /**
     * 加载解析器的实现对象
     *
     * @return YamlProvider
     */
    protected static @Nonnull YamlLoader loadYamlLoader() {
        if (currentYamlLoader == null) {
            Iterator<YamlProvider> it = ServiceLoader.load(YamlProvider.class).iterator();
            RedkaleClassLoader.putServiceLoader(YamlProvider.class);
            List<YamlProvider> providers = new ArrayList<>();
            while (it.hasNext()) {
                YamlProvider provider = it.next();
                if (provider != null) {
                    RedkaleClassLoader.putReflectionPublicConstructors(
                            provider.getClass(), provider.getClass().getName());
                    providers.add(provider);
                }
            }
            for (YamlProvider provider : Utility.sortPriority(providers)) {
                YamlLoader leader = provider.createLoader();
                if (leader != null) {
                    currentYamlLoader = leader;
                    return leader;
                }
            }
            currentYamlLoader = new DefaultYamlLoader();
        }
        return currentYamlLoader;
    }

    protected static class DefaultYamlLoader implements YamlLoader {

        @Override
        public AnyValue read(String content) {
            throw new UnsupportedOperationException("Not supported yaml.");
        }
    }
}
