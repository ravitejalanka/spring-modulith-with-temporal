package com.example.modulith.temporal.config

import com.example.modulith.temporal.activity.*
import com.example.modulith.temporal.workflow.OrderFulfillmentWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "temporal")
data class TemporalProperties(
    val serviceAddress: String = "localhost:7233",
    val namespace: String = "default",
    val taskQueue: String = "order-fulfillment-task-queue"
)

/**
 * Temporal configuration
 */
@Configuration
@EnableConfigurationProperties(TemporalProperties::class)
class TemporalConfig {

    companion object {
        const val TASK_QUEUE = "order-fulfillment-task-queue"
    }

    @Bean
    fun workflowServiceStubs(properties: TemporalProperties): WorkflowServiceStubs {
        val options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(properties.serviceAddress)
            .build()

        return WorkflowServiceStubs.newServiceStubs(options)
    }

    @Bean
    fun workflowClient(
        workflowServiceStubs: WorkflowServiceStubs,
        properties: TemporalProperties
    ): WorkflowClient {
        val options = WorkflowClientOptions.newBuilder()
            .setNamespace(properties.namespace)
            .build()

        return WorkflowClient.newInstance(workflowServiceStubs, options)
    }

    @Bean
    fun workerFactory(workflowClient: WorkflowClient): WorkerFactory {
        return WorkerFactory.newInstance(workflowClient)
    }

    @Bean
    fun worker(
        workerFactory: WorkerFactory,
        orderActivity: OrderActivityImpl,
        paymentActivity: PaymentActivityImpl,
        fulfillmentActivity: FulfillmentActivityImpl,
        properties: TemporalProperties
    ): Worker {
        val worker = workerFactory.newWorker(properties.taskQueue)

        // Register workflows
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl::class.java)

        // Register activities
        worker.registerActivitiesImplementations(
            orderActivity,
            paymentActivity,
            fulfillmentActivity
        )

        return worker
    }
}
