/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.redkale.util.Utility.hexToBin;

/**
 * Flow简单的操作
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.5.0
 */
public abstract class Flows {

    /**
     *
     *
     * <blockquote>
     *
     * <pre>
     * public class AnonymousMonoFutureFunction implements java.util.function.Function&lt;Object, java.util.concurrent.CompletableFuture&gt; {
     *
     *      &#64;Override
     *      public java.util.concurrent.CompletableFuture apply(Object t) {
     *          return ((reactor.core.publisher.Mono) t).toFuture();
     *      }
     *
     * }
     * </pre>
     *
     * </blockquote>
     */
    private static final String FUNCTION_MONO_FUTRUE_BINARY = "cafebabe0000003700220a000200030700040c0005000"
            + "60100106a6176612f6c616e672f4f626a6563740100063c696e69743e01000328295607000801001b72656163746f722f636f72652"
            + "f7075626c69736865722f4d6f6e6f0a0007000a0c000b000c010008746f46757475726501002a28294c6a6176612f7574696c2f636"
            + "f6e63757272656e742f436f6d706c657461626c654675747572653b0a000e000f0700100c0011001201002c6f72672f7265646b616"
            + "c652f7574696c2f416e6f6e796d6f75734d6f6e6f46757475726546756e6374696f6e0100056170706c7901003c284c6a6176612f6"
            + "c616e672f4f626a6563743b294c6a6176612f7574696c2f636f6e63757272656e742f436f6d706c657461626c654675747572653b0"
            + "7001401001b6a6176612f7574696c2f66756e6374696f6e2f46756e6374696f6e010004436f646501000f4c696e654e756d6265725"
            + "461626c650100124c6f63616c5661726961626c655461626c650100047468697301002e4c6f72672f7265646b616c652f7574696c2"
            + "f416e6f6e796d6f75734d6f6e6f46757475726546756e6374696f6e3b010001740100124c6a6176612f6c616e672f4f626a6563743"
            + "b0100104d6574686f64506172616d6574657273010026284c6a6176612f6c616e672f4f626a6563743b294c6a6176612f6c616e672"
            + "f4f626a6563743b0100095369676e617475726501006b4c6a6176612f6c616e672f4f626a6563743b4c6a6176612f7574696c2f667"
            + "56e6374696f6e2f46756e6374696f6e3c4c6a6176612f6c616e672f4f626a6563743b4c6a6176612f7574696c2f636f6e637572726"
            + "56e742f436f6d706c657461626c654675747572653b3e3b01000a536f7572636546696c65010020416e6f6e796d6f75734d6f6e6f4"
            + "6757475726546756e6374696f6e2e6a6176610021000e00020001001300000003000100050006000100150000002f0001000100000"
            + "0052ab70001b10000000200160000000600010000000c00170000000c0001000000050018001900000001001100120002001500000"
            + "03c00010002000000082bc00007b60009b000000002001600000006000100000010001700000016000200000008001800190000000"
            + "00008001a001b0001001c0000000501001a000010410011001d000200150000003000020002000000062a2bb6000db000000002001"
            + "60000000600010000000c00170000000c000100000006001800190000001c0000000501001a10000002001e00000002001f0020000"
            + "000020021";

    /**
     *
     *
     * <blockquote>
     *
     * <pre>
     * public class AnonymousFluxFutureFunction implements java.util.function.Function&lt;Object, java.util.concurrent.CompletableFuture&gt; {
     *
     *      &#64;Override
     *      public java.util.concurrent.CompletableFuture apply(Object t) {
     *          return ((reactor.core.publisher.Flux) t).collectList().toFuture();
     *      }
     *
     * }
     * </pre>
     *
     * </blockquote>
     */
    private static final String FUNCTION_FLUX_FUTRUE_BINARY = "cafebabe0000003700280a000200030700040c0005000"
            + "60100106a6176612f6c616e672f4f626a6563740100063c696e69743e01000328295607000801001b72656163746f722f636f72"
            + "652f7075626c69736865722f466c75780a0007000a0c000b000c01000b636f6c6c6563744c69737401001f28294c72656163746"
            + "f722f636f72652f7075626c69736865722f4d6f6e6f3b0a000e000f0700100c0011001201001b72656163746f722f636f72652f"
            + "7075626c69736865722f4d6f6e6f010008746f46757475726501002a28294c6a6176612f7574696c2f636f6e63757272656e742"
            + "f436f6d706c657461626c654675747572653b0a001400150700160c0017001801002c6f72672f7265646b616c652f7574696c2f"
            + "416e6f6e796d6f7573466c757846757475726546756e6374696f6e0100056170706c7901003c284c6a6176612f6c616e672f4f6"
            + "26a6563743b294c6a6176612f7574696c2f636f6e63757272656e742f436f6d706c657461626c654675747572653b07001a0100"
            + "1b6a6176612f7574696c2f66756e6374696f6e2f46756e6374696f6e010004436f646501000f4c696e654e756d6265725461626"
            + "c650100124c6f63616c5661726961626c655461626c650100047468697301002e4c6f72672f7265646b616c652f7574696c2f41"
            + "6e6f6e796d6f7573466c757846757475726546756e6374696f6e3b010001740100124c6a6176612f6c616e672f4f626a6563743"
            + "b0100104d6574686f64506172616d6574657273010026284c6a6176612f6c616e672f4f626a6563743b294c6a6176612f6c616e"
            + "672f4f626a6563743b0100095369676e617475726501006b4c6a6176612f6c616e672f4f626a6563743b4c6a6176612f7574696"
            + "c2f66756e6374696f6e2f46756e6374696f6e3c4c6a6176612f6c616e672f4f626a6563743b4c6a6176612f7574696c2f636f6e"
            + "63757272656e742f436f6d706c657461626c654675747572653b3e3b01000a536f7572636546696c65010020416e6f6e796d6f7"
            + "573466c757846757475726546756e6374696f6e2e6a61766100210014000200010019000000030001000500060001001b000000"
            + "2f00010001000000052ab70001b100000002001c0000000600010000000c001d0000000c000100000005001e001f00000001001"
            + "700180002001b0000003f000100020000000b2bc00007b60009b6000db000000002001c00000006000100000010001d00000016"
            + "00020000000b001e001f00000000000b00200021000100220000000501002000001041001700230002001b00000030000200020"
            + "00000062a2bb60013b000000002001c0000000600010000000c001d0000000c000100000006001e001f00000022000000050100"
            + "201000000200240000000200250026000000020027";

    private static final Class reactorMonoClass;

    private static final Class reactorFluxClass;

    private static final Function<Object, CompletableFuture> reactorMonoFunction;

    private static final Function<Object, CompletableFuture> reactorFluxFunction;

    static {
        Class reactorMonoClass0 = null;
        Class reactorFluxClass0 = null;
        Function<Object, CompletableFuture> reactorMonoFunction0 = null;
        Function<Object, CompletableFuture> reactorFluxFunction0 = null;

        if (!"executable".equals(System.getProperty("org.graalvm.nativeimage.kind"))) { // not native-image
            try {
                //
                RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
                reactorMonoClass0 = classLoader.loadClass("reactor.core.publisher.Mono");
                Class<Function<Object, CompletableFuture>> monoFuncClass = null;
                try {
                    monoFuncClass = classLoader.loadClass("org.redkale.util.AnonymousMonoFutureFunction");
                } catch (Throwable t) {
                    // do nothing
                }
                if (monoFuncClass == null) {
                    byte[] classBytes = hexToBin(FUNCTION_MONO_FUTRUE_BINARY);
                    monoFuncClass = classLoader.loadClass("org.redkale.util.AnonymousMonoFutureFunction", classBytes);
                }
                RedkaleClassLoader.putReflectionDeclaredConstructors(monoFuncClass, monoFuncClass.getName());
                reactorMonoFunction0 = monoFuncClass.getDeclaredConstructor().newInstance();
                //
                reactorFluxClass0 = classLoader.loadClass("reactor.core.publisher.Flux");
                Class<Function<Object, CompletableFuture>> fluxFuncClass = null;
                try {
                    fluxFuncClass = classLoader.loadClass("org.redkale.util.AnonymousFluxFutureFunction");
                } catch (Throwable t) {
                    // do nothing
                }
                if (fluxFuncClass == null) {
                    byte[] classBytes = hexToBin(FUNCTION_FLUX_FUTRUE_BINARY);
                    fluxFuncClass = classLoader.loadClass("org.redkale.util.AnonymousFluxFutureFunction", classBytes);
                }
                RedkaleClassLoader.putReflectionDeclaredConstructors(fluxFuncClass, fluxFuncClass.getName());
                reactorFluxFunction0 = fluxFuncClass.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                // do nothing
            }
        }

        reactorMonoClass = reactorMonoClass0;
        reactorFluxClass = reactorFluxClass0;
        reactorMonoFunction = reactorMonoFunction0;
        reactorFluxFunction = reactorFluxFunction0;
    }

    Flows() {}

    public static boolean maybePublisherClass(Class value) {
        if (value == null) {
            return false;
        }
        if (reactorFluxFunction != null) {
            if (reactorMonoClass.isAssignableFrom(value)) {
                return true;
            }
            if (reactorFluxClass.isAssignableFrom(value)) {
                return true;
            }
        }
        return Flow.Publisher.class.isAssignableFrom(value);
    }

    public static Type maybePublisherSubType(Type value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType pt = (ParameterizedType) value;
        Type parent = pt.getRawType() == null ? pt.getOwnerType() : pt.getRawType();
        if (!(parent instanceof Class)) {
            return null;
        }
        if (pt.getActualTypeArguments().length != 1) {
            return null;
        }
        if (reactorFluxFunction != null) {
            if (reactorMonoClass.isAssignableFrom((Class) parent)) {
                return pt.getActualTypeArguments()[0];
            }
            if (reactorFluxClass.isAssignableFrom((Class) parent)) {
                return pt.getActualTypeArguments()[0];
            }
        }
        if (Flow.Publisher.class.isAssignableFrom((Class) parent)) {
            return pt.getActualTypeArguments()[0];
        }
        return null;
    }

    public static Object maybePublisherToFuture(Object value) {
        if (value == null) {
            return value;
        }
        if (reactorFluxFunction != null) {
            Class clazz = value.getClass();
            if (reactorMonoClass.isAssignableFrom(clazz)) {
                return reactorMonoFunction.apply(value);
            }
            if (reactorFluxClass.isAssignableFrom(clazz)) {
                return reactorFluxFunction.apply(value);
            }
            if (Flow.Publisher.class.isAssignableFrom(clazz)) {
                return createMonoFuture((Flow.Publisher) value);
            }
        }
        return value;
    }

    public static final <T> CompletableFuture<List<T>> createFluxFuture(Flow.Publisher<T> publisher) {
        SubscriberListFuture<T> future = new SubscriberListFuture<>();
        publisher.subscribe(future);
        return future;
    }

    public static final <T> CompletableFuture<T> createMonoFuture(Flow.Publisher<T> publisher) {
        SubscriberFuture<T> future = new SubscriberFuture<>();
        publisher.subscribe(future);
        return future;
    }

    /**
     * 简单的CompletableFuture与Flow.Subscriber的结合类。
     *
     * <p>详情见: https://redkale.org
     *
     * @since 2.5.0
     * @param <T> T
     */
    public static class SubscriberFuture<T> extends CompletableFuture<T> implements Flow.Subscriber<T> {

        protected T rs;

        @Override
        public void onSubscribe(Flow.Subscription s) {
            s.request(Integer.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            rs = item;
        }

        @Override
        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            complete(rs);
        }
    }

    /**
     * 简单的CompletableFuture与Flow.Subscriber的结合类。
     *
     * <p>详情见: https://redkale.org
     *
     * @since 2.5.0
     * @param <T> T
     */
    public static class SubscriberListFuture<T> extends CompletableFuture<List<T>> implements Flow.Subscriber<T> {

        protected List<T> rs;

        @Override
        public void onSubscribe(Flow.Subscription s) {
            s.request(Integer.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            if (rs == null) {
                rs = new ArrayList<>();
            }
            rs.add(item);
        }

        @Override
        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            complete(rs == null ? new ArrayList<>() : rs);
        }
    }
}
