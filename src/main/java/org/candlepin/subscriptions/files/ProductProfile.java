/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.files;

import static org.candlepin.subscriptions.db.model.Granularity.*;

import org.candlepin.subscriptions.db.model.Granularity;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents information telling capacity and tally how to handle certain products
 */
public class ProductProfile {

    private static final ProductProfile DEFAULT_PROFILE = new ProductProfile("DEFAULT",
        Collections.emptySet(), DAILY);

    public static ProductProfile getDefault() {
        return DEFAULT_PROFILE;
    }

    private String name;
    private Set<String> productIds;
    private Granularity finestGranularity;
    private boolean burstable = false;
    private String prometheusMetricName;
    private String prometheusCounterName;

    public ProductProfile() {
        // Default used for YAML deserialization
    }

    public ProductProfile(String name, Set<String> productIds, Granularity granularity) {
        this.name = name;
        this.productIds = productIds;
        this.finestGranularity = granularity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(Set<String> productIds) {
        this.productIds = productIds;
    }

    public Granularity getFinestGranularity() {
        return finestGranularity;
    }

    public void setFinestGranularity(Granularity finestGranularity) {
        this.finestGranularity = finestGranularity;
    }

    public boolean isBurstable() {
        return burstable;
    }

    public void setBurstable(boolean burstable) {
        this.burstable = burstable;
    }

    public String getPrometheusMetricName() {
        return prometheusMetricName;
    }

    public void setPrometheusMetricName(String prometheusMetricName) {
        this.prometheusMetricName = prometheusMetricName;
    }

    public String getPrometheusCounterName() {
        return prometheusCounterName;
    }

    public void setPrometheusCounterName(String prometheusCounterName) {
        this.prometheusCounterName = prometheusCounterName;
    }

    public boolean supportsProduct(String productId) {
        return productIds.contains(productId);
    }

    public boolean prometheusEnabled() {
        return StringUtils.hasText(prometheusCounterName) || StringUtils.hasText(prometheusMetricName);
    }

    public boolean supportsGranularity(Granularity granularity) {
        return granularity.compareTo(finestGranularity) < 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProductProfile that = (ProductProfile) o;
        return burstable == that.burstable && Objects.equals(name, that.name) &&
            Objects.equals(productIds, that.productIds) && finestGranularity == that.finestGranularity &&
            Objects.equals(prometheusMetricName, that.prometheusMetricName) &&
            Objects.equals(prometheusCounterName, that.prometheusCounterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, productIds, finestGranularity, burstable, prometheusMetricName,
            prometheusCounterName);
    }
}