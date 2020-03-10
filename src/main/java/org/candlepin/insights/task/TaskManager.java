/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.task;

import org.candlepin.insights.orgsync.OrgListStrategy;
import org.candlepin.insights.orgsync.OrgSyncProperties;
import org.candlepin.insights.task.queue.TaskQueue;

import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * A TaskManager is an injectable component that is responsible for putting tasks into
 * the TaskQueue. This is the entry point into the task system. Any class that would like
 * to initiate a task within conduit, should inject this class and call the appropriate
 * method.
 */
@Component
public class TaskManager {
    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    @Autowired
    TaskQueueProperties taskQueueProperties;

    @Autowired
    OrgSyncProperties orgSyncProperties;

    @Autowired
    OrgListStrategy orgListStrategy;

    @Autowired
    TaskQueue queue;

    /**
     * Initiates a task that will update the inventory of the specified organization's ID.
     *
     * @param orgId the ID of the org in which to update.
     */
    @SuppressWarnings("indentation")
    public void updateOrgInventory(String orgId) {
        queue.enqueue(
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, taskQueueProperties.getTaskGroup())
                .setArg("org_id", orgId)
                .build()
        );
    }

    /**
     * Queue up tasks for each configured org.
     *
     * @throws JobExecutionException if the org list can't be fetched
     */
    public void syncFullOrgList() throws IOException {
        List<String> orgsToSync;

        orgsToSync = orgListStrategy.getOrgsToSync();

        if (orgSyncProperties.getLimit() != null) {
            Integer size = orgsToSync.size();
            Integer limit = orgSyncProperties.getLimit();
            log.info("Fetched {} orgs to sync, but limiting to {}", size, limit);
            if (limit < size) {
                orgsToSync = orgsToSync.subList(0, limit);
            }
        }
        log.info("Starting inventory update for {} orgs", orgsToSync.size());
        orgsToSync.forEach(org -> {
            try {
                updateOrgInventory(org);
            }
            catch (Exception e) {
                log.error("Could not update inventory for org: {}", org, e);
            }
        });
        log.info("Inventory update complete.");
    }
}
