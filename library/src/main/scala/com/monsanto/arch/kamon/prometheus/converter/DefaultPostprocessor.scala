package com.monsanto.arch.kamon.prometheus.converter

import com.monsanto.arch.kamon.prometheus.metric.MetricFamily

/** The default post-processor which adds some help text to certain predefined metric families.  Currently, this
  * supports `kamon-akka`’s actor and dispatcher metrics.
  *
  * @author Daniel Solano Gómez
  */
class DefaultPostprocessor extends Postprocessor {
  import DefaultPostprocessor._

  /** Post-processes a metric family. */
  override def apply(metricFamily: MetricFamily): MetricFamily = {
    metricFamily.name match {
      case "akka_actor_time_in_mailbox_nanoseconds" ⇒
        metricFamily.withHelp(AkkaActorTimeInMailboxHelp)
      case "akka_actor_processing_time_nanoseconds" ⇒
        metricFamily.withHelp(AkkaActorProcessingTimeHelp)
      case "akka_actor_mailbox_size" ⇒
        metricFamily.withHelp(AkkaActorMailboxSizeHelp)
      case "akka_actor_errors" ⇒
        metricFamily.withHelp(AkkaActorErrorsHelp)
      case "akka_fork_join_pool_dispatcher_parallelism" ⇒
        metricFamily.withHelp(AkkaForkJoinPoolDispatcherParallelismHelp)
      case "akka_fork_join_pool_dispatcher_pool_size" ⇒
        metricFamily.withHelp(AkkaForkJoinPoolDispatcherPoolSizeHelp)
      case "akka_fork_join_pool_dispatcher_active_threads" ⇒
        metricFamily.withHelp(AkkaForkJoinPoolDispatcherActiveThreadsHelp)
      case "akka_fork_join_pool_dispatcher_running_threads" ⇒
        metricFamily.withHelp(AkkaForkJoinPoolDispatcherRunningThreadsHelp)
      case "akka_fork_join_pool_dispatcher_queued_task_count" ⇒
        metricFamily.withHelp(AkkaForkJoinPoolDispatcherQueuedTaskCountHelp)
      case "akka_thread_pool_executor_dispatcher_core_pool_size" ⇒
        metricFamily.withHelp(AkkaThreadPoolExecutorDispatcherCorePoolSizeHelp)
      case "akka_thread_pool_executor_dispatcher_max_pool_size" ⇒
        metricFamily.withHelp(AkkaThreadPoolExecutorDispatcherMaxPoolSizeHelp)
      case "akka_thread_pool_executor_dispatcher_pool_size" ⇒
        metricFamily.withHelp(AkkaThreadPoolExecutorDispatcherPoolSizeHelp)
      case "akka_thread_pool_executor_dispatcher_active_threads" ⇒
        metricFamily.withHelp(AkkaThreadPoolExecutorDispatcherActiveThreadsHelp)
      case "akka_thread_pool_executor_dispatcher_processed_tasks" ⇒
        metricFamily.withHelp(AkkaThreadPoolExecutorDispatcherProcessedTasksHelp)
      case "akka_router_routing_time_nanoseconds" ⇒
        metricFamily.withHelp(AkkaRouterRoutingTimeHelp)
      case "akka_router_time_in_mailbox_nanoseconds" ⇒
        metricFamily.withHelp(AkkaRouterTimeInMailboxHelp)
      case "akka_router_processing_time_nanoseconds" ⇒
        metricFamily.withHelp(AkkaRouterProcessingTimeHelp)
      case "akka_router_errors" ⇒
        metricFamily.withHelp(AkkaRouterErrorsHelp)
      case _ ⇒ metricFamily
    }
  }
}

object DefaultPostprocessor {
  /** Help text for time in mailbox metrics for Akka actors. */
  val AkkaActorTimeInMailboxHelp = "Tracks the time measured from the moment a message was enqueued into an actor’s " +
    "mailbox until the moment it was dequeued for processing"
  /** Help text for processing time metrics for Akka actors. */
  val AkkaActorProcessingTimeHelp = "Tracks how long did it take for the actor to process every message."
  /** Help text for mailbox size metrics for Akka actors. */
  val AkkaActorMailboxSizeHelp = "Tracks the size of the actor’s mailbox"
  /** Help text for errors metrics for Akka actors. */
  val AkkaActorErrorsHelp = "Counts the number of failures the actor has experienced"
  /** Help text for parallelism metrics for fork join pool dispatchers. */
  val AkkaForkJoinPoolDispatcherParallelismHelp = "The desired parallelism value for the fork join pool, remains " +
    "steady over time"
  /** Help text for pool size metrics for fork join pool dispatchers. */
  val AkkaForkJoinPoolDispatcherPoolSizeHelp = "The number of worker threads that have started but not yet " +
    "terminated.  This value will differ from that of akka_fork_join_pool_dispatcher_parallelism if threads are " +
    "created to maintain parallelism when others are cooperatively blocked."
  /** Help text for active thread metrics for fork join pool dispatchers. */
  val AkkaForkJoinPoolDispatcherActiveThreadsHelp = "An estimate of the number of worker threads that are currently " +
    "stealing or executing tasks"
  /** Help text for running thread metrics for fork join pool dispatchers. */
  val AkkaForkJoinPoolDispatcherRunningThreadsHelp = "An estimate of the number of worker threads that are not " +
    "blocked waiting to join tasks or for other managed synchronisation"
  /** Help text for queued task count metrics for fork join pool dispatchers. */
  val AkkaForkJoinPoolDispatcherQueuedTaskCountHelp = "An approximation of the total number of tasks currently held " +
    "in queues by worker threads (but not including tasks submitted to the pool that have not begun executing)"
  /** Help text for core pool size metrics for thread pool executor dispatchers. */
  val AkkaThreadPoolExecutorDispatcherCorePoolSizeHelp = "The core pool size of the executor"
  /** Help text for max pool size metrics for thread pool executor dispatchers. */
  val AkkaThreadPoolExecutorDispatcherMaxPoolSizeHelp = "The maximum number of threads allowed by the executor"
  /** Help text for pool size metrics for thread pool executor dispatchers. */
  val AkkaThreadPoolExecutorDispatcherPoolSizeHelp = "The current number of threads in the pool"
  /** Help text for active thread metrics for thread pool executor dispatchers. */
  val AkkaThreadPoolExecutorDispatcherActiveThreadsHelp = "The number of threads actively executing tasks"
  /** Help text for processed task metrics for thread pool executor dispatchers. */
  val AkkaThreadPoolExecutorDispatcherProcessedTasksHelp = "The number of processed tasks for the executor"
  /** Help text for routing time metrics for Akka routers. */
  val AkkaRouterRoutingTimeHelp = "Tracks how long it took the router to decide which routee will process a message"
  /** Help text for time in mailbox metrics for Akka routers. */
  val AkkaRouterTimeInMailboxHelp = "Tracks the combined time measured from the moment a message was enqueued into a " +
    "routee‘s mailbox until the moment it was dequeued for processing."
  /** Help text for processing time metrics for Akka routers. */
  val AkkaRouterProcessingTimeHelp = "Tracks how long it took for routees to process incoming messages"
  /** Help text for error metrics for Akka routers. */
  val AkkaRouterErrorsHelp = "Tracks how long it took for routees to process incoming messages"
}
