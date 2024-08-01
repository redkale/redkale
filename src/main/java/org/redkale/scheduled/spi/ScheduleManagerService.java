/*
 *
 */
package org.redkale.scheduled.spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.boot.Application;
import org.redkale.net.sncp.Sncp;
import org.redkale.scheduled.Scheduled;
import org.redkale.scheduled.ScheduledEvent;
import org.redkale.scheduled.ScheduledManager;
import org.redkale.service.LoadMode;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * 定时任务管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Local
@Component
@AutoLoad(false)
@ResourceType(ScheduledManager.class)
public class ScheduleManagerService implements ScheduledManager, Service {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<WeakReference, List<ScheduledTask>> refTaskMap = new ConcurrentHashMap<>();

    protected final ReentrantLock lock = new ReentrantLock();

    @Resource(required = false)
    protected Application application;

    @Nullable
    private UnaryOperator<String> propertyFunc;

    private ScheduledThreadPoolExecutor scheduler;

    protected boolean enabled = true;

    protected AnyValue config;

    protected ScheduleManagerService(UnaryOperator<String> propertyFunc) {
        this.propertyFunc = propertyFunc;
    }

    // 一般用于独立组件
    public static ScheduleManagerService create(UnaryOperator<String> propertyFunc) {
        return new ScheduleManagerService(propertyFunc);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public ScheduleManagerService enabled(boolean val) {
        this.enabled = val;
        return this;
    }

    @Override
    public void init(AnyValue conf) {
        if (conf == null) {
            conf = AnyValue.create();
        }
        this.config = conf;
        this.enabled = config.getBoolValue("enabled", true);
        if (this.enabled) {
            if (this.propertyFunc == null && application != null) {
                UnaryOperator<String> func = application.getEnvironment()::getPropertyValue;
                this.propertyFunc = func;
            }
            this.scheduler = new ScheduledThreadPoolExecutor(
                    Utility.cpus(), Utility.newThreadFactory("Redkale-Scheduled-Task-Thread-%s"));
            this.scheduler.setRemoveOnCancelPolicy(true);
        }
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void onServersPreStart() {
        // do nothing
    }

    public void onServersPostStart() {
        // do nothing
    }

    @Override
    public void schedule(Object service) {
        lock.lock();
        try {
            boolean remoteMode = service instanceof Service && Sncp.isRemote((Service) service);
            for (WeakReference item : refTaskMap.keySet()) {
                if (item.get() == service) {
                    logger.log(Level.WARNING, service + " repeat schedule");
                }
            }
            Map<String, ScheduledTask> tasks = new LinkedHashMap<>();
            Class clazz = service.getClass();
            WeakReference ref = new WeakReference(service);
            Set<String> methodKeys = new HashSet<>();
            do {
                for (final Method method : clazz.getDeclaredMethods()) {
                    if (method.getAnnotation(Scheduled.class) == null) {
                        continue;
                    }
                    String mk = Utility.methodKey(method);
                    if (methodKeys.contains(mk)) {
                        // 跳过已处理的继承方法
                        continue;
                    }
                    methodKeys.add(mk);
                    if (method.getParameterCount() != 0
                            && !(method.getParameterCount() == 1
                                    && method.getParameterTypes()[0] == ScheduledEvent.class)) {
                        throw new RedkaleException(
                                "@" + Scheduled.class.getSimpleName() + " must be on non-parameter or "
                                        + ScheduledEvent.class.getSimpleName() + "-parameter method, but on " + method);
                    }
                    ScheduledTask task = schedule(ref, method, remoteMode);
                    // 时间没配置: task=null
                    if (task != null) {
                        tasks.put(method.getName(), task);
                        RedkaleClassLoader.putReflectionMethod(clazz.getName(), method);
                    }
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            // 开始执行定时任务
            if (enabled && !tasks.isEmpty()) {
                tasks.forEach((name, task) -> task.init());
                refTaskMap.put(ref, new ArrayList<>(tasks.values()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unschedule(Object service) {
        lock.lock();
        try {
            for (Map.Entry<WeakReference, List<ScheduledTask>> item : refTaskMap.entrySet()) {
                if (item.getKey().get() == service) {
                    refTaskMap.remove(item.getKey());
                    for (ScheduledTask task : item.getValue()) {
                        task.stop();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    protected ScheduledTask schedule(WeakReference ref, Method method, boolean remoteMode) {
        Scheduled ann = method.getAnnotation(Scheduled.class);
        if (!LoadMode.matches(remoteMode, ann.mode())) {
            return null;
        }
        String name = getProperty(ann.name());
        String cron = getProperty(ann.cron());
        String fixedDelay = getProperty(ann.fixedDelay());
        String fixedRate = getProperty(ann.fixedRate());
        String initialDelay = getProperty(ann.initialDelay());
        String zone = getProperty(ann.zone());
        TimeUnit timeUnit = ann.timeUnit();
        return scheduleTask(ref, method, name, cron, fixedDelay, fixedRate, initialDelay, zone, timeUnit);
    }

    protected ScheduledTask scheduleTask(
            WeakReference ref,
            Method method,
            String name,
            String cron,
            String fixedDelay,
            String fixedRate,
            String initialDelay,
            String zone,
            TimeUnit timeUnit) {
        if ((cron.isEmpty() || "-".equals(cron)) && "-1".equals(fixedRate) && "-1".endsWith(fixedDelay)) {
            return createdOnlyNameTask(
                    ref, method, name, cron, fixedDelay, fixedRate, initialDelay, zone, timeUnit); // 时间都没配置
        }
        ZoneId zoneId = Utility.isEmpty(zone) ? null : ZoneId.of(zone);
        if (!cron.isEmpty() && !"-".equals(cron)) {
            CronExpression cronExpr = CronExpression.parse(cron);
            return new CronTask(ref, name, method, cronExpr, zoneId);
        } else {
            long fixedDelayLong = Long.parseLong(fixedDelay);
            long fixedRateLong = Long.parseLong(fixedRate);
            long initialDelayLong = Long.parseLong(initialDelay);
            return new FixedTask(ref, name, method, fixedDelayLong, fixedRateLong, initialDelayLong, timeUnit);
        }
    }

    protected ScheduledTask createdOnlyNameTask(
            WeakReference ref,
            Method method,
            String name,
            String cron,
            String fixedDelay,
            String fixedRate,
            String initialDelay,
            String zone,
            TimeUnit timeUnit) {
        return null;
    }

    protected Function<ScheduledEvent, Object> createFuncJob(final WeakReference ref, Method method) {
        try {
            if (!Modifier.isPublic(method.getModifiers())) {
                method.setAccessible(true);
            }
            MethodHandle mh = MethodHandles.lookup().unreflect(method);
            return event -> {
                Object rs = null;
                try {
                    Object obj = ref.get();
                    if (obj != null) {
                        if (event == null) {
                            rs = mh.invoke(obj);
                        } else {
                            rs = mh.invoke(obj, event);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "schedule task error", t);
                }
                if (event != null) {
                    event.clear();
                }
                return rs;
            };
        } catch (IllegalAccessException e) {
            throw new RedkaleException(e);
        }
    }

    protected String getProperty(String value) {
        if (propertyFunc == null || value == null || value.indexOf('}') < 0) {
            return value;
        }
        return propertyFunc.apply(value);
    }

    @Override
    public int start(String scheduleName) {
        int c = 0;
        lock.lock();
        try {
            for (Map.Entry<WeakReference, List<ScheduledTask>> item : refTaskMap.entrySet()) {
                for (ScheduledTask task : item.getValue()) {
                    if (Objects.equals(task.name(), scheduleName)) {
                        c++;
                        task.start();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return c;
    }

    @Override
    public List<Object> execute(String scheduleName, boolean all) {
        List<Object> rs = new ArrayList<>();
        lock.lock();
        try {
            for (Map.Entry<WeakReference, List<ScheduledTask>> item : refTaskMap.entrySet()) {
                for (ScheduledTask task : item.getValue()) {
                    if (Objects.equals(task.name(), scheduleName)) {
                        rs.add(task.execute());
                        if (!all) {
                            return rs;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return rs;
    }

    @Override
    public int stop(String scheduleName) {
        int c = 0;
        lock.lock();
        try {
            for (Map.Entry<WeakReference, List<ScheduledTask>> item : refTaskMap.entrySet()) {
                for (ScheduledTask task : item.getValue()) {
                    if (Objects.equals(task.name(), scheduleName)) {
                        c++;
                        task.stop();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return c;
    }

    protected abstract class ScheduledTask {

        protected final WeakReference ref;

        protected final String name;

        protected final Method method;

        protected final AtomicBoolean started = new AtomicBoolean();

        protected ScheduledFuture future;

        protected final ScheduledEvent event;

        protected final Map<String, Object> eventMap;

        // 任务是否正运行中
        protected final AtomicBoolean doing = new AtomicBoolean();

        protected ScheduledTask(WeakReference ref, String name, Method method) {
            Objects.requireNonNull(ref);
            Objects.requireNonNull(name);
            Objects.requireNonNull(method);
            this.ref = ref;
            this.name = name;
            this.method = method;
            this.eventMap = method.getParameterCount() == 0 ? null : new HashMap<>();
            this.event = eventMap == null ? null : new ScheduledEvent(eventMap);
        }

        public void init() {
            start();
        }

        public abstract void start();

        protected abstract Function<ScheduledEvent, Object> delegate();

        public Object execute() {
            Object rs = null;
            doing.set(true);
            try {
                rs = delegate().apply(event);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "ScheduledTask[" + name() + "] schedule error", t);
            } finally {
                doing.set(false);
            }
            return rs;
        }

        public void stop() {
            if (future != null) {
                future.cancel(true);
                future = null;
            }
            this.started.set(false);
        }

        public boolean doing() {
            return doing.get();
        }

        public Map<String, Object> eventMap() {
            return eventMap;
        }

        public Method method() {
            return method;
        }

        public String name() {
            return name;
        }
    }

    protected class FixedTask extends ScheduledTask implements Runnable {

        private final Function<ScheduledEvent, Object> delegate;

        private final long fixedDelay;

        private final long fixedRate;

        private final long initialDelay;

        private final TimeUnit timeUnit;

        public FixedTask(
                final WeakReference ref,
                String name,
                Method method,
                long fixedDelay,
                long fixedRate,
                long initialDelay,
                TimeUnit timeUnit) {
            super(ref, name, method);
            this.delegate = createFuncJob(ref, method);
            this.fixedDelay = fixedDelay;
            this.fixedRate = fixedRate;
            this.initialDelay = initialDelay;
            this.timeUnit = timeUnit;
        }

        @Override
        protected Function<ScheduledEvent, Object> delegate() {
            return delegate;
        }

        @Override
        public void run() {
            super.execute();
            if (ref.get() == null) {
                super.stop();
            }
        }

        @Override
        public void start() {
            if (started.compareAndSet(false, true)) {
                if (fixedRate > 0) {
                    this.future = scheduler.scheduleAtFixedRate(
                            this, initialDelay > 0 ? initialDelay : 0, fixedRate, timeUnit);
                } else if (fixedDelay > 0) {
                    this.future = scheduler.scheduleWithFixedDelay(this, initialDelay, fixedDelay, timeUnit);
                } else if (initialDelay > 0) {
                    this.future = scheduler.schedule(this, initialDelay, timeUnit);
                }
            }
        }
    }

    protected class CronTask extends ScheduledTask implements Runnable {

        private final Function<ScheduledEvent, Object> delegate;

        private final CronExpression cron;

        @Nullable
        private final ZoneId zoneId;

        public CronTask(WeakReference ref, String name, Method method, CronExpression cron, ZoneId zoneId) {
            super(ref, name, method);
            this.delegate = createFuncJob(ref, method);
            this.cron = cron;
            this.zoneId = zoneId;
        }

        @Override
        protected Function<ScheduledEvent, Object> delegate() {
            return delegate;
        }

        @Override
        public void run() {
            super.execute();
            schedule();
        }

        @Override
        public void start() {
            if (ref.get() == null) {
                return;
            }
            if (started.compareAndSet(false, true)) {
                schedule();
            }
        }

        private void schedule() {
            if (started.get()) {
                LocalDateTime now = zoneId == null ? LocalDateTime.now() : LocalDateTime.now(zoneId);
                LocalDateTime next = cron.next(now);
                Duration delay = Duration.between(now, next);
                this.future = scheduler.schedule(this, delay.toNanos(), TimeUnit.NANOSECONDS);
            }
        }
    }
}
