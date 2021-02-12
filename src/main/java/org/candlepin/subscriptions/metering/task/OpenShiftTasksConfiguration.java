/*
 * Copyright (c) 2021 Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.metering.task;

import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMeteringTaskFactory;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.task.TaskFactory;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.queue.TaskConsumer;
import org.candlepin.subscriptions.task.queue.TaskConsumerFactory;
import org.candlepin.subscriptions.task.queue.TaskQueue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Defines the required/custom bean declarations for setting up a task queue
 * for gathering openshift metrics from prometheus.
 *
 * It is intended to be an imported configuration that should be used by a
 * profile when an openshift metrics task queue is required.
 *
 * NOTE: The configuration annotation has been omitted on purpose as it
 *       should be imported.
 */
public class OpenShiftTasksConfiguration {

    // Qualify this bean so that a new instance is created in the case that another
    // queue is configured for another topic.
    @Bean
    @Qualifier("openshiftTaskQueueProperties")
    @ConfigurationProperties(prefix = "rhsm-subscriptions.metering.openshift.tasks")
    TaskQueueProperties meteringTaskQueueProperties() {
        return new TaskQueueProperties();
    }

    @Bean
    public PrometheusMetricsTaskManager metricsTaskManager(TaskQueue queue,
        @Qualifier("openshiftTaskQueueProperties") TaskQueueProperties queueProps,
        AccountListSource accounts) {
        return new PrometheusMetricsTaskManager(queue, queueProps, accounts);
    }

    // The following beans are defined for the worker profile only allowing
    // separation of producer/consumers in profiles (if required).
    @Bean
    @Qualifier("prometheusTaskFactory")
    @Profile("openshift-metering-worker")
    TaskFactory meteringTaskFactory(PrometheusMeteringController controller) {
        return new PrometheusMeteringTaskFactory(controller);
    }

    @Bean
    @Qualifier("openshiftMeteringTaskConsumer")
    @Profile("openshift-metering-worker")
    public TaskConsumer meteringTaskProcessor(
        @Qualifier("openshiftTaskQueueProperties") TaskQueueProperties taskQueueProperties,
        TaskConsumerFactory<? extends TaskConsumer> taskConsumerFactory,
        @Qualifier("prometheusTaskFactory") TaskFactory taskFactory) {

        return taskConsumerFactory.createTaskConsumer(taskFactory, taskQueueProperties);
    }

}
