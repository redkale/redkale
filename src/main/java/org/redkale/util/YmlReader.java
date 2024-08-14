/*

*/

package org.redkale.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.redkale.annotation.Nonnull;

/**
 * 简单的yml读取器
 * TODO 待实现
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.8.0
 * @author zhangjx
 */
public class YmlReader {

    private static YmlProvider currentProvider;

    private final String text;

    public YmlReader(String text) {
        this.text = Objects.requireNonNull(text);
    }

    public AnyValue read() {
        return loadProvider().read(text);
    }

    /**
     * 加载解析器的实现对象
     *
     * @return YmlProvider
     */
    private static @Nonnull YmlProvider loadProvider() {
        if (currentProvider == null) {
            Iterator<YmlProvider> it = ServiceLoader.load(YmlProvider.class).iterator();
            RedkaleClassLoader.putServiceLoader(YmlProvider.class);
            List<YmlProvider> providers = new ArrayList<>();
            while (it.hasNext()) {
                YmlProvider provider = it.next();
                if (provider != null) {
                    RedkaleClassLoader.putReflectionPublicConstructors(
                            provider.getClass(), provider.getClass().getName());
                    providers.add(provider);
                }
            }
            for (YmlProvider provider : Utility.sortPriority(providers)) {
                currentProvider = provider;
                return provider;
            }
            currentProvider = new DefaultYmlProvider();
        }
        return currentProvider;
    }

    protected static class DefaultYmlProvider implements YmlProvider {

        @Override
        public AnyValue read(String content) {
            throw new UnsupportedOperationException("Not supported yml.");
        }
    }
}
