/*
 *
 */
package org.redkale.boot;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.Environment;

/**
 * 各组件的引擎类, 由Application管理
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class ModuleEngine {

    protected final Logger logger = Logger.getLogger(getClass().getSimpleName());

    protected final Application application;

    protected final ResourceFactory resourceFactory;

    protected final Environment environment;

    public ModuleEngine(Application application) {
        this.application = application;
        this.resourceFactory = Objects.requireNonNull(application.resourceFactory);
        this.environment = Objects.requireNonNull(application.getEnvironment());
    }

    /**
     * 判断模块的配置项合并策略， 返回null表示模块不识别此配置项
     *
     * @param path 配置项路径
     * @param key 配置项名称
     * @param val1 配置项原值
     * @param val2 配置项新值
     * @return MergeEnum
     */
    public AnyValue.MergeEnum mergeAppConfigStrategy(String path, String key, AnyValue val1, AnyValue val2) {
        return null;
    }

    /**
     * 动态扩展类的方法
     *
     * @param remote 是否远程模式
     * @param serviceClass 类
     * @return 方法动态扩展器
     */
    public AsmMethodBoost createAsmMethodBoost(boolean remote, Class serviceClass) {
        return null;
    }

    /** 进入Application.init方法时被调用 此时状态: 1、远程配置项未获取 2、WorkExecutor未初始化 */
    public void onAppPreInit() {
        // do nothing
    }

    /** 结束Application.init方法前被调用 */
    public void onAppPostInit() {
        // do nothing
    }

    /** 进入Application.start方法被调用 */
    public void onAppPreStart() {
        // do nothing
    }

    /** 结束Application.start方法前被调用 */
    public void onAppPostStart() {
        // do nothing
    }

    /**
     * 配置项加载后被调用
     *
     * @param allProps 配置项全量
     */
    public void onEnvironmentLoaded(Properties allProps) {
        // do nothing
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events 变更项
     */
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        // do nothing
    }

    /** Application 在运行Compile前调用 */
    public void onPreCompile() {
        // do nothing
    }

    /** Application 在运行Compile后调用 */
    public void onPostCompile() {
        // do nothing
    }

    /** 服务全部启动前被调用 */
    public void onServersPreStart() {
        // do nothing
    }

    /** 服务全部启动后被调用 */
    public void onServersPostStart() {
        // do nothing
    }

    /**
     * 执行Service.init方法前被调用
     *
     * @param service Service
     */
    public void onServicePreInit(Service service) {
        // do nothing
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    public void onServicePostInit(Service service) {
        // do nothing
    }

    /**
     * 执行Service.destroy方法前被调用
     *
     * @param service Service
     */
    public void onServicePreDestroy(Service service) {
        // do nothing
    }

    /**
     * 执行Service.destroy方法后被调用
     *
     * @param service Service
     */
    public void onServicePostDestroy(Service service) {
        // do nothing
    }

    /** 服务全部停掉前被调用 */
    public void onServersPreStop() {
        // do nothing
    }

    /** 服务全部停掉后被调用 */
    public void onServersPostStop() {
        // do nothing
    }

    /** 进入Application.shutdown方法被调用 */
    public void onAppPreShutdown() {
        // do nothing
    }

    /** 结束Application.shutdown方法前被调用 */
    public void onAppPostShutdown() {
        // do nothing
    }
}
