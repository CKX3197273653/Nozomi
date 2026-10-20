package org.mate.mate10.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;


/*
* 异步线程池,因为资源画像完全不同所以将chat和agent两个分开,
* 对话: 请求多,单次几秒 -> 池子可以大一些
* agent:请求少,单词[实测26s],每次 5000+token和7次工具调用
*     -> 池子给小,共用一个池子4个agent就能占满core.普通对话全被饿死
*
*
* 拒绝策略用"抛异常"不是CallerRunsPolicy
* CallerRunsPolicy 会让[提交者]自己跑,这里的提交者是tomcat线程
* 任务要跑20S+ Tomcat线程会被占住,从而不接受新的请求
* 并发一高整个应用直接失去响应,就连health check都打不开
* 异常由Controller捕获 -> 立刻发给前端 error+ done,用户会立刻知道 "服务器繁忙"不要干等
*
* 队列给小 队列 50 + core 4 = 54个任务才会扩容
* 也就是54个请求只用4个线程,其余50个在排队
* 排队比拒绝还难受,用户看不到反馈,队列给小,让"过载" 快速暴露
* */

@Slf4j
@Configuration
public class ExecutorConfig {
    //普通对话的流式执行池
    @Bean("chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor(){
        return buildExecutor(
                "chat-stream-",
                4,
                8,
                10,
                30,
                "对话");
    }

    //Agent分析(L1)的流式执行池 -- 单次几十秒,给小一些
    @Bean("agentStreamExecutor")
    public ThreadPoolTaskExecutor agentStreamExecutor(){
        return buildExecutor(
                "agent-stream-",
                2,
                4,
                5,
                60,
                "Agent");
    }

    private ThreadPoolTaskExecutor buildExecutor(String namePrefix,
                                                 int core,
                                                 int max,
                                                 int queue,
                                                 int awaitSeconds,
                                                 String label){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(namePrefix);

        //池满状态:日志+抛异常(由Controller 转成 SSE error + done)
        executor.setRejectedExecutionHandler(rejectingHandler(label));

        //在跑任务收尾,不是硬切,不然前端收不到done
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitSeconds);
    executor.initialize();
        log.info("[Executor] {} 池就绪 core={} max={} queue={} awaitTermination={}s",
                label, core, max, queue, awaitSeconds);
    return executor;
    }

    //拒绝时打日志( 用于监控到底有没有过载,再抛出去让上层处理)
    //不静默丢弃, 不让提交者线程跑长任务

    private RejectedExecutionHandler rejectingHandler(String label) {
        return (task, executor) -> {
            log.warn("[Executor] {} 池已满 → 拒绝任务 active={} poolSize={} queue={}",
                    label, executor.getActiveCount(),
                    executor.getPoolSize(), executor.getQueue().size());
            throw new TaskRejectedException(label + " 线程池已满，请稍后重试");
        };
    }
}
