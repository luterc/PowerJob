package tech.powerjob.server.remote.worker;

import tech.powerjob.common.model.DeployedContainerInfo;
import tech.powerjob.server.common.module.WorkerInfo;
import tech.powerjob.server.extension.WorkerFilter;
import tech.powerjob.server.persistence.remote.model.JobInfoDO;
import tech.powerjob.server.remote.server.redirector.DesignateServer;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 获取 worker 集群信息
 *
 * @author tjq
 * @since 2021/2/19
 */
@Slf4j
@Service
public class WorkerClusterQueryService {

    private List<WorkerFilter> workerFilters;

    @Autowired
    public WorkerClusterQueryService(List<WorkerFilter> workerFilters) {
        this.workerFilters = workerFilters;
    }

    /**
     * get worker for job
     * @param jobInfo job
     * @return worker cluster info, sorted by metrics desc
     */
    public List<WorkerInfo> getSuitableWorkers(JobInfoDO jobInfo) {

        List<WorkerInfo> workers = Lists.newLinkedList(getWorkerInfosByAppId(jobInfo.getAppId()).values());

        workers.removeIf(workerInfo -> filterWorker(workerInfo, jobInfo));

        // 按健康度排序
        workers.sort((o1, o2) -> o2 .getSystemMetrics().calculateScore() - o1.getSystemMetrics().calculateScore());

        // 限定集群大小（0代表不限制）
        if (!workers.isEmpty() && jobInfo.getMaxWorkerCount() > 0 && workers.size() > jobInfo.getMaxWorkerCount()) {
            workers = workers.subList(0, jobInfo.getMaxWorkerCount());
        }
        return workers;
    }

    @DesignateServer
    public List<WorkerInfo> getAllWorkers(Long appId) {
        List<WorkerInfo> workers = Lists.newLinkedList(getWorkerInfosByAppId(appId).values());
        workers.sort((o1, o2) -> o2 .getSystemMetrics().calculateScore() - o1.getSystemMetrics().calculateScore());
        return workers;
    }

    /**
     * get all alive workers
     * @param appId appId
     * @return alive workers
     */
    public List<WorkerInfo> getAllAliveWorkers(Long appId) {
        List<WorkerInfo> workers = Lists.newLinkedList(getWorkerInfosByAppId(appId).values());
        workers.removeIf(WorkerInfo::timeout);
        return workers;
    }

    public Optional<WorkerInfo> getWorkerInfoByAddress(Long appId, String address) {
        return Optional.ofNullable(getWorkerInfosByAppId(appId).get(address));
    }

    public Map<Long, ClusterStatusHolder> getAppId2ClusterStatus() {
        return WorkerClusterManagerService.getAppId2ClusterStatus();
    }

    /**
     * 获取某个应用容器的部署情况
     * @param appId 应用ID
     * @param containerId 容器ID
     * @return 部署情况
     */
    public List<DeployedContainerInfo> getDeployedContainerInfos(Long appId, Long containerId) {
        ClusterStatusHolder clusterStatusHolder = getAppId2ClusterStatus().get(appId);
        if (clusterStatusHolder == null) {
            return Collections.emptyList();
        }
        return clusterStatusHolder.getDeployedContainerInfos(containerId);
    }

    private Map<String, WorkerInfo> getWorkerInfosByAppId(Long appId) {
        ClusterStatusHolder clusterStatusHolder = getAppId2ClusterStatus().get(appId);
        if (clusterStatusHolder == null) {
            log.warn("[WorkerManagerService] can't find any worker for app(appId={}) yet.", appId);
            return Collections.emptyMap();
        }
        return clusterStatusHolder.getAllWorkers();
    }

    /**
     * filter invalid worker for job
     * @param workerInfo worker info
     * @param jobInfo job info
     * @return filter this worker when return true
     */
    private boolean filterWorker(WorkerInfo workerInfo, JobInfoDO jobInfo) {
        for (WorkerFilter filter : workerFilters) {
            if (filter.filter(workerInfo, jobInfo)) {
                return true;
            }
        }
        return false;
    }
}
