package org.skywalking.apm.collector.stream;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;
import org.skywalking.apm.collector.queue.QueueModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.LocalAsyncWorkerProviderDefineLoader;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.RemoteWorkerProviderDefineLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StreamModuleInstaller extends SingleModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(StreamModuleInstaller.class);

    @Override public String groupName() {
        return StreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new StreamModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        List<String> dependenceModules = new LinkedList<>();
        dependenceModules.add(QueueModuleGroupDefine.GROUP_NAME);
        return dependenceModules;
    }

    /**
     * 关联客户端样本信息处理worker
     * apm-collector-stream初始化入口
     * 被cluster组依赖
     * @throws DefineException
     */
    @Override public void onAfterInstall() throws DefineException {
        initializeWorker((StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(groupName()));
    }

    private void initializeWorker(StreamModuleContext context) throws DefineException {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext();
        context.setClusterWorkerContext(clusterWorkerContext);
        /**
         * 读取本地持久化stream worker
         * locate_worker_provider.define
         */
        LocalAsyncWorkerProviderDefineLoader localAsyncProviderLoader = new LocalAsyncWorkerProviderDefineLoader();
        /**
         * 读取远程持久化stream worker
         * remote_worker_provider.define
         */
        RemoteWorkerProviderDefineLoader remoteProviderLoader = new RemoteWorkerProviderDefineLoader();
        try {
            List<AbstractLocalAsyncWorkerProvider> localAsyncProviders = localAsyncProviderLoader.load();
            for (AbstractLocalAsyncWorkerProvider provider : localAsyncProviders) {
                provider.setClusterContext(clusterWorkerContext);
                //给worker绑定队列
                provider.create();
                //指定worker均衡负载规则、数据规则
                clusterWorkerContext.putRole(provider.role());
            }

            List<AbstractRemoteWorkerProvider> remoteProviders = remoteProviderLoader.load();
            for (AbstractRemoteWorkerProvider provider : remoteProviders) {
                //绑定到上下文
                provider.setClusterContext(clusterWorkerContext);
                //将worker添加到context
                clusterWorkerContext.putRole(provider.role());
                clusterWorkerContext.putProvider(provider);
            }
        } catch (ProviderNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
