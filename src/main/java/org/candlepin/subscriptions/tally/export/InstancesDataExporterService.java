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
package org.candlepin.subscriptions.tally.export;

import static org.candlepin.subscriptions.resource.InstancesResource.getCategoryByMeasurementType;
import static org.candlepin.subscriptions.resource.InstancesResource.getCloudProviderByMeasurementType;
import static org.candlepin.subscriptions.resource.InstancesResource.getHardwareMeasurementTypesFromCategory;
import static org.candlepin.subscriptions.resource.InstancesResource.isPayg;
import static org.candlepin.subscriptions.resource.ResourceUtils.ANY;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallyInstanceViewRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyInstanceView;
import org.candlepin.subscriptions.db.model.TallyInstancesDbReportCriteria;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.candlepin.subscriptions.export.DataExporterService;
import org.candlepin.subscriptions.export.ExportServiceRequest;
import org.candlepin.subscriptions.json.InstancesExportGuest;
import org.candlepin.subscriptions.json.InstancesExportItem;
import org.candlepin.subscriptions.json.InstancesExportMetric;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("worker")
public class InstancesDataExporterService
    implements DataExporterService<TallyInstanceView, InstancesExportItem> {

  public static final String INSTANCES_DATA = "instances";
  public static final String PRODUCT_ID = "product_id";
  private static final String BEGINNING = "beginning";
  private static final int BAD_REQUEST = Response.Status.BAD_REQUEST.getStatusCode();
  private static final int MAX_GUESTS_PER_QUERY = 20;
  private static final Map<
          String,
          BiConsumer<TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder, String>>
      FILTERS =
          Map.of(
              PRODUCT_ID,
              InstancesDataExporterService::handleProductIdFilter,
              "usage",
              InstancesDataExporterService::handleUsageFilter,
              "category",
              InstancesDataExporterService::handleCategoryFilter,
              "sla",
              InstancesDataExporterService::handleSlaFilter,
              "metric_id",
              InstancesDataExporterService::handleMetricIdFilter,
              "billing_provider",
              InstancesDataExporterService::handleBillingProviderFilter,
              "billing_account_id",
              InstancesDataExporterService::handleBillingAccountIdFilter,
              BEGINNING,
              InstancesDataExporterService::handleMonthFilter);

  private static final List<String> MANDATORY_FILTERS = List.of(PRODUCT_ID);

  private final TallyInstanceViewRepository tallyViewRepository;
  private final HostRepository hostRepository;

  @Autowired
  public InstancesDataExporterService(
      TallyInstanceViewRepository tallyViewRepository, HostRepository hostRepository) {
    this.tallyViewRepository = tallyViewRepository;
    this.hostRepository = hostRepository;
  }

  @Override
  public boolean handles(ExportServiceRequest request) {
    return Objects.equals(request.getRequest().getResource(), INSTANCES_DATA);
  }

  @Override
  public Stream<TallyInstanceView> fetchData(ExportServiceRequest request) {
    log.debug("Fetching data for {}", request.getOrgId());
    var reportCriteria = extractExportFilter(request);
    return tallyViewRepository.streamBy(reportCriteria);
  }

  @Override
  public InstancesExportItem mapDataItem(TallyInstanceView item, ExportServiceRequest request) {
    var instance = new InstancesExportItem();
    instance.setId(item.getId());
    instance.setInstanceId(item.getKey().getInstanceId());
    instance.setDisplayName(item.getDisplayName());
    if (item.getHostBillingProvider() != null) {
      instance.setBillingProvider(item.getHostBillingProvider().getValue());
    }
    var category = getCategoryByMeasurementType(item.getKey().getMeasurementType());
    if (category != null) {
      instance.setCategory(category.toString());
    }

    var cloudProvider = getCloudProviderByMeasurementType(item.getKey().getMeasurementType());
    if (cloudProvider != null) {
      instance.setCloudProvider(cloudProvider.toString());
    }

    instance.setBillingAccountId(item.getHostBillingAccountId());
    instance.setMeasurements(new ArrayList<>());
    var variant = Variant.findByTag(item.getKey().getProductId());
    boolean isPayg = isPayg(variant);
    String month = isPayg ? getMonthFromFiltersOrUseNow(request) : null;
    var metrics = MetricIdUtils.getMetricIdsFromConfigForVariant(variant.orElse(null)).toList();
    for (var metric : metrics) {
      instance
          .getMeasurements()
          .add(toInstanceExportMetric(metric, resolveMetricValue(item, metric, month, isPayg)));
    }

    instance.setLastSeen(item.getLastSeen());
    instance.setNumberOfGuests(item.getNumOfGuests());
    instance.setSubscriptionManagerId(item.getSubscriptionManagerId());
    instance.setInventoryId(item.getInventoryId());
    if (item.getNumOfGuests() > 0) {
      var guestsInInstance =
          getGuestHostsByHypervisorInstanceId(item, PageRequest.ofSize(MAX_GUESTS_PER_QUERY));
      Set<InstancesExportGuest> guests =
          new HashSet<>(mapHostGuests(guestsInInstance.getContent()));
      while (guestsInInstance.hasNext()) {
        guestsInInstance =
            getGuestHostsByHypervisorInstanceId(item, guestsInInstance.nextPageable());
        guests.addAll(mapHostGuests(guestsInInstance.getContent()));
      }

      instance.setGuests(new ArrayList<>(guests));
    }
    return instance;
  }

  @Override
  public Class<TallyInstanceView> getDataClass() {
    return TallyInstanceView.class;
  }

  @Override
  public Class<InstancesExportItem> getExportItemClass() {
    return InstancesExportItem.class;
  }

  private Page<Host> getGuestHostsByHypervisorInstanceId(
      TallyInstanceView item, Pageable pageRequest) {
    return hostRepository.getGuestHostsByHypervisorInstanceId(
        item.getOrgId(), item.getKey().getInstanceId(), pageRequest);
  }

  private List<InstancesExportGuest> mapHostGuests(List<Host> guests) {
    return guests.stream()
        .map(
            guest ->
                new InstancesExportGuest()
                    .withDisplayName(guest.getDisplayName())
                    .withHardwareType(
                        guest.getHardwareType() == null ? null : guest.getHardwareType().toString())
                    .withInsightsId(guest.getInsightsId())
                    .withInventoryId(guest.getInventoryId())
                    .withSubscriptionManagerId(guest.getSubscriptionManagerId())
                    .withLastSeen(guest.getLastSeen())
                    .withCloudProvider(guest.getCloudProvider())
                    .withIsUnmappedGuest(guest.isUnmappedGuest())
                    .withIsHypervisor(guest.isHypervisor()))
        .toList();
  }

  private TallyInstancesDbReportCriteria extractExportFilter(ExportServiceRequest request) {
    var report =
        TallyInstancesDbReportCriteria.builder()
            .orgId(request.getOrgId())
            // defaults: it will be overwritten by the provided filters if set
            .sla(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .billingProvider(BillingProvider._ANY)
            .billingAccountId(ANY)
            .month(InstanceMonthlyTotalKey.formatMonthId(OffsetDateTime.now()));
    var mandatoryFilters = new ArrayList<>(MANDATORY_FILTERS);
    if (request.getFilters() != null) {
      var filters = request.getFilters().entrySet();
      try {
        for (var entry : filters) {
          mandatoryFilters.remove(entry.getKey());
          var filterHandler = FILTERS.get(entry.getKey().toLowerCase(Locale.ROOT));
          if (filterHandler == null) {
            log.warn("Filter '{}' isn't currently supported. Ignoring.", entry.getKey());
          } else if (entry.getValue() != null) {
            filterHandler.accept(report, entry.getValue().toString());
          }
        }

      } catch (IllegalArgumentException ex) {
        throw new ExportServiceException(
            BAD_REQUEST, "Wrong filter in export request: " + ex.getMessage());
      }
    }

    if (!mandatoryFilters.isEmpty()) {
      throw new ExportServiceException(
          BAD_REQUEST, "Missing mandatory filters: " + mandatoryFilters);
    }

    // special handling of the month for non payg products
    if (!isPayg(Variant.findByTag(report.build().getProductId()))) {
      report.month(null);
    }

    return report.build();
  }

  private static void handleProductIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.productId(ProductId.fromString(value).toString());
  }

  private static void handleSlaFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    ServiceLevel serviceLevel = ServiceLevel.fromString(value);
    if (value.equalsIgnoreCase(serviceLevel.getValue())) {
      builder.sla(serviceLevel);
    } else {
      throw new IllegalArgumentException(String.format("sla: %s not supported", value));
    }
  }

  private static void handleUsageFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    Usage usage = Usage.fromString(value);
    if (value.equalsIgnoreCase(usage.getValue())) {
      builder.usage(usage);
    } else {
      throw new IllegalArgumentException(String.format("usage: %s not supported", value));
    }
  }

  private static void handleMetricIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.metricId(MetricId.fromString(value));
  }

  private static void handleBillingProviderFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    BillingProvider billingProvider = BillingProvider.fromString(value);
    if (value.equalsIgnoreCase(billingProvider.getValue())) {
      builder.billingProvider(billingProvider);
    } else {
      throw new IllegalArgumentException(
          String.format("billing_provider: %s not supported", value));
    }
  }

  private static void handleBillingAccountIdFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.billingAccountId(value);
  }

  private static void handleMonthFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    try {
      var date = OffsetDateTime.parse(value);
      builder.month(InstanceMonthlyTotalKey.formatMonthId(date));
    } catch (Exception ex) {
      throw new IllegalArgumentException(String.format("beginning: value %s not supported", value));
    }
  }

  private static void handleCategoryFilter(
      TallyInstancesDbReportCriteria.TallyInstancesDbReportCriteriaBuilder builder, String value) {
    builder.hardwareMeasurementTypes(
        getHardwareMeasurementTypesFromCategory(ReportCategory.fromString(value)));
  }

  private static InstancesExportMetric toInstanceExportMetric(MetricId metric, double value) {
    return new InstancesExportMetric().withMetricId(metric.toString()).withValue(value);
  }

  private static double resolveMetricValue(
      TallyInstanceView item, MetricId metricId, String month, boolean isPayg) {
    if (metricId.equals(MetricIdUtils.getSockets())) {
      return item.getSockets();
    } else if (metricId.equals(MetricIdUtils.getCores())) {
      return item.getCores();
    } else if (!isPayg && item.getKey().getMetricId().equalsIgnoreCase(metricId.toString())) {
      return item.getValue();
    }

    return Optional.ofNullable(item.getMonthlyTotal(month, metricId)).orElse(0.0);
  }

  private static String getMonthFromFiltersOrUseNow(ExportServiceRequest request) {
    OffsetDateTime beginning = OffsetDateTime.now();
    if (request.getFilters() != null) {
      Object value = request.getFilters().get(BEGINNING);
      if (value instanceof String str) {
        beginning = OffsetDateTime.parse(str);
      }
    }

    return InstanceMonthlyTotalKey.formatMonthId(beginning);
  }
}
