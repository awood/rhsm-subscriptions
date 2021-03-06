/*
 * Copyright Red Hat, Inc.
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Properties related to all metrics that are to be gathered from the prometheus service. */
@Getter
@Setter
@ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.metric")
public class PrometheusMetricsProperties {

  public static final String OPENSHIFT_PRODUCT_PROFILE_ID = "OpenShift";

  private MetricProperties openshift = new MetricProperties();

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Map<Uom, MetricProperties> getSupportedMetricsForProduct(String productProfileId) {
    if (!productProfileId.equals(OPENSHIFT_PRODUCT_PROFILE_ID)) {
      throw new UnsupportedOperationException(
          "Only OpenShift product profile ID is currently supported");
    }
    return Map.of(Uom.CORES, openshift);
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Collection<String> getMetricsEnabledProductProfiles() {
    return List.of(OPENSHIFT_PRODUCT_PROFILE_ID);
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public String getEnabledAccountPromQLforProductProfile(String productProfileId) {
    // NOTE(khowell): doesn't make sense for a given product profile (e.g. OSD) to have different
    // queries per-metric. Grabbing the first non-empty one for now.
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getEnabledAccountPromQL)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElseThrow();
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Integer getMetricsTimeoutForProductProfile(String productProfileId) {
    // NOTE(khowell): doesn't make sense for a given product profile (e.g. OSD) to have different
    // metrics timeouts. Grabbing the first one for now.
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getQueryTimeout)
        .findFirst()
        .orElseThrow();
  }

  // ENT-3835 will change the data structures used here and should refactor this method as needed
  public Integer getRangeInMinutesForProductProfile(String productProfileId) {
    return getSupportedMetricsForProduct(productProfileId).values().stream()
        .map(MetricProperties::getRangeInMinutes)
        .findFirst()
        .orElseThrow();
  }
}
