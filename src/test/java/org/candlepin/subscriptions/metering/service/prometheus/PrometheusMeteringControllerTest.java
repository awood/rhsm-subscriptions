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
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
public class PrometheusMeteringControllerTest {

    @Mock
    private PrometheusService service;

    @Test
    void testMeteringExceptionWhenServiceReturnsError() throws Exception {
        QueryResult errorResponse = new QueryResult();
        errorResponse.setStatus(StatusType.ERROR);
        errorResponse.setError("FORCED!!");

        when(service.getOpenshiftData(anyString(), any(), any())).thenReturn(errorResponse);

        PrometheusMeteringController controller = new PrometheusMeteringController(service);
        Throwable e = assertThrows(MeteringException.class, () -> controller.reportOpenshiftMetrics(
            "account", OffsetDateTime.now(), OffsetDateTime.now()));
        assertEquals("Unable to fetch openshift metrics: FORCED!!", e.getMessage());
    }

    @Test
    @SuppressWarnings("indentation")
    void willPersistEvents() throws Exception {
        QueryResult data = new QueryResult()
        .status(StatusType.SUCCESS)
        .data(new QueryResultData()
            .resultType(ResultType.MATRIX)
            .addResultItem(
                new QueryResultDataResult()
                    .putMetricItem("_id", "C1")
                    .addValuesItem(Arrays.asList(BigDecimal.valueOf(123456.234), BigDecimal.valueOf(120L)))
                    .addValuesItem(Arrays.asList(BigDecimal.valueOf(124456.234), BigDecimal.valueOf(126L)))
            )
        );
        when(service.getOpenshiftData(eq("my-account"),
            any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(data);

        PrometheusMeteringController controller = new PrometheusMeteringController(service);
        controller.reportOpenshiftMetrics("my-account", OffsetDateTime.now(),
            OffsetDateTime.now());

        // TODO Verify that the repository called save for each metric received. Will need to
        //      add other labels (putMetricItem) in there as well. This will be done in upcoming
        //      persist Events task.
    }
}