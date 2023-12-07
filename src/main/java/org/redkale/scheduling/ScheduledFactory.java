/*
 *
 */
package org.redkale.scheduling;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Scheduled;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * 定时任务工厂
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ScheduledFactory {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final ConcurrentHashMap<WeakReference, List<ScheduledTask>> refTaskMap = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    @Nullable
    private final UnaryOperator<String> propertyFunc;

    private final ScheduledThreadPoolExecutor scheduler;

    private boolean disable;

    protected ScheduledFactory(UnaryOperator<String> propertyFunc) {
        this.propertyFunc = propertyFunc;
        this.scheduler = new ScheduledThreadPoolExecutor(Utility.cpus(), Utility.newThreadFactory("Scheduled-Task-Thread-%s"));
        this.scheduler.setRemoveOnCancelPolicy(true);
    }

    public static ScheduledFactory create(UnaryOperator<String> propertyFunc) {
        return new ScheduledFactory(propertyFunc);
    }

    public ScheduledFactory disable(boolean val) {
        this.disable = val;
        return this;
    }

    public void unschedule(Object service) {
        lock.lock();
        try {
            Map.Entry<WeakReference, List<ScheduledTask>> entry = null;
            for (Map.Entry<WeakReference, List<ScheduledTask>> item : refTaskMap.entrySet()) {
                if (item.getKey().get() == service) {
                    entry = item;
                    break;
                }
            }
            if (entry == null) {
                return;
            }
            refTaskMap.remove(entry.getKey());
            for (ScheduledTask task : entry.getValue()) {
                task.cancel();
            }
        } finally {
            lock.unlock();
        }
    }

    public void schedule(Object service) {
        lock.lock();
        try {
            for (WeakReference item : refTaskMap.keySet()) {
                if (item.get() == service) {
                    logger.log(Level.WARNING, service + " repeat schedule");
                    return;
                }
            }
            Map<String, ScheduledTask> tasks = new LinkedHashMap<>();
            Class clazz = service.getClass();
            WeakReference ref = new WeakReference(service);
            do {
                for (final Method method : clazz.getDeclaredMethods()) {
                    if (method.getAnnotation(Scheduled.class) == null) {
                        continue;
                    }
                    if (tasks.containsKey(method.getName())) {
                        continue;
                    }
                    if (method.getParameterCount() > 0) {
                        throw new RedkaleException("@" + Scheduled.class.getSimpleName() + " must be on non-parameter method, but on " + method);
                    }
                    ScheduledTask task = schedule(ref, method);
                    if (task == null) {
                        continue;  //时间都没配置
                    }
                    tasks.put(method.getName(), task);
                    RedkaleClassLoader.putReflectionMethod(clazz.getName(), method);
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            //开始执行定时任务
            if (!disable && !tasks.isEmpty()) {
                tasks.forEach((name, task) -> task.start());
                refTaskMap.put(ref, new ArrayList<>(tasks.values()));
            }
        } finally {
            lock.unlock();
        }
    }

    protected ScheduledTask schedule(WeakReference ref, Method method) {
        Scheduled ann = method.getAnnotation(Scheduled.class);
        String name = getProperty(ann.name());
        String cron = getProperty(ann.cron());
        String fixedDelay = getProperty(ann.fixedDelay());
        String fixedRate = getProperty(ann.fixedRate());
        String initialDelay = getProperty(ann.initialDelay());
        String zone = getProperty(ann.zone());
        TimeUnit timeUnit = ann.timeUnit();
        if ((cron.isEmpty() || "-".equals(cron)) && "-1".equals(fixedRate) && "-1".endsWith(fixedDelay)) {
            return null;  //时间都没配置
        }
        ZoneId zoneId = Utility.isEmpty(zone) ? null : ZoneId.of(zone);
        if (!cron.isEmpty() && !"-".equals(cron)) {
            CronExpression cronExpr = CronExpression.parse(cron);
            return new CronTask(ref, name, method, cronExpr, zoneId);
        } else {
            long fixedDelayLong = getLongValue(fixedDelay);
            long fixedRateLong = getLongValue(fixedRate);
            long initialDelayLong = getLongValue(initialDelay);
            return new FixedTask(ref, name, method, fixedDelayLong, fixedRateLong, initialDelayLong, timeUnit);
        }
    }

    protected Runnable createRunnable(final WeakReference ref, Method method) {
        try {
            if (!Modifier.isPublic(method.getModifiers())) {
                method.setAccessible(true);
            }
            MethodHandle mh = MethodHandles.lookup().unreflect(method);
            return () -> {
                try {
                    Object obj = ref.get();
                    if (obj != null) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "schedule task " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
                        }
                        mh.invoke(obj);
                    }
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "schedule task error", t);
                }
            };
        } catch (IllegalAccessException e) {
            throw new RedkaleException(e);
        }
    }

    protected String getProperty(String value) {
        if (propertyFunc == null || value.indexOf('}') < 0) {
            return value;
        }
        return propertyFunc.apply(value);
    }

    //支持5*60乘法表达式
    protected long getLongValue(String value) {
        if (value.indexOf('*') > -1) {
            long rs = 1;
            boolean flag = false;
            for (String v : value.split("\\*")) {
                if (!v.trim().isEmpty()) {
                    rs *= Long.parseLong(v.trim());
                    flag = true;
                }
            }
            return flag ? rs : -1;
        }
        return Long.parseLong(value);
    }

    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    protected abstract class ScheduledTask implements Runnable {

        protected final WeakReference ref;

        protected final String name;

        protected final Method method;

        protected ScheduledFuture future;

        protected ScheduledTask(WeakReference ref, String name, Method method) {
            Objects.requireNonNull(ref);
            Objects.requireNonNull(name);
            Objects.requireNonNull(method);
            this.ref = ref;
            this.name = name;
            this.method = method;
        }

        public abstract void start();

        public void cancel() {
            if (future != null) {
                future.cancel(true);
            }
        }

        public Method method() {
            return method;
        }

        public String name() {
            return name;
        }
    }

    protected class FixedTask extends ScheduledTask {

        private final Runnable delegate;

        private final long fixedDelay;

        private final long fixedRate;

        private final long initialDelay;

        private final TimeUnit timeUnit;

        public FixedTask(final WeakReference ref, String name, Method method, long fixedDelay, long fixedRate, long initialDelay, TimeUnit timeUnit) {
            super(ref, name, method);
            this.delegate = createRunnable(ref, method);
            this.fixedDelay = fixedDelay;
            this.fixedRate = fixedRate;
            this.initialDelay = initialDelay;
            this.timeUnit = timeUnit;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "schedule task error", t);
            }
            if (ref.get() == null) {
                cancel();
            }
        }

        @Override
        public void start() {
            if (fixedRate > 0) {
                this.future = scheduler.scheduleAtFixedRate(this, initialDelay > 0 ? initialDelay : 0, fixedRate, timeUnit);
            } else if (fixedDelay > 0) {
                this.future = scheduler.scheduleWithFixedDelay(this, initialDelay, fixedDelay, timeUnit);
            } else if (initialDelay > 0) {
                this.future = scheduler.schedule(this, initialDelay, timeUnit);
            }
        }
    }

    protected class CronTask extends ScheduledTask {

        private final Runnable delegate;

        private final CronExpression cron;

        @Nullable
        private final ZoneId zoneId;

        public CronTask(WeakReference ref, String name, Method method, CronExpression cron, ZoneId zoneId) {
            super(ref, name, method);
            this.delegate = createRunnable(ref, method);
            this.cron = cron;
            this.zoneId = zoneId;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "schedule task error", t);
            }
            start();
        }

        @Override
        public void start() {
            if (ref.get() == null) {
                return;
            }
            LocalDateTime now = zoneId == null ? LocalDateTime.now() : LocalDateTime.now(zoneId);
            LocalDateTime next = cron.next(now);
            Duration delay = Duration.between(now, next);
            this.future = scheduler.schedule(this, delay.toNanos(), TimeUnit.NANOSECONDS);
        }
    }
}
