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
package org.candlepin.subscriptions.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {OfferingSyncControllerTest.TestProductConfiguration.class})
@ActiveProfiles({"worker", "test"})
class OfferingSyncControllerTest {

  @TestConfiguration
  static class TestProductConfiguration {
    @Bean
    @Qualifier("product")
    @Primary
    public HttpClientProperties productServiceTestProperties() {
      HttpClientProperties props = new HttpClientProperties();
      props.setUseStub(true);
      return props;
    }

    @Bean
    @Primary
    public ProductApiFactory productApiTestFactory(
        @Qualifier("product") HttpClientProperties props) {
      return new ProductApiFactory(props);
    }
  }

  @MockBean OfferingRepository repo;
  @MockBean ProductWhitelist allowlist;
  @Autowired OfferingSyncController subject;

  @BeforeEach
  void init() {
    when(allowlist.productIdMatches(anyString())).thenReturn(true);
  }

  @Test
  void testSyncOfferingNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findById(anyString())).thenReturn(Optional.empty());

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingChanged() {
    // Given an Offering that is different from what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Arrays.asList(68));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the updated Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingUnchanged() {
    // Given an Offering that is equal to what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Arrays.asList(68, 69, 70, 71, 72));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then no persisting should happen.
    verify(repo, never()).save(sku);
  }

  @Test
  void testSyncOfferingNoProductIdsShouldPersist() {
    // Given an Offering that has no engineering product ids,
    Offering sku = new Offering();
    sku.setSku("MW01484"); // This is an actual Offering that has no engineering product ids

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then it should still persist, since there are Offerings that we need that have no eng prods.
    verify(repo).save(sku);
  }

  @Test
  void testGetUpstreamOfferingForOcpOffering() {
    // Given a marketing SKU for OpenShift Container Platform
    var sku = "MW01485";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Arrays.asList("SVCMW01485"));
    expected.setProductIds(
        Arrays.asList(
            69, 70, 185, 194, 197, 201, 205, 240, 271, 290, 311, 317, 318, 326, 329, 408, 458, 473,
            479, 491, 518, 519, 546, 579, 588, 603, 604, 608, 610, 645));
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Container Platform");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForNoEngProductOffering() {
    // Given a marketing SKU MW01484 (special for being engProduct-less),
    var sku = "MW01484";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Arrays.asList("SVCMW01484A", "SVCMW01484B"));
    expected.setProductIds(Collections.emptyList());
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Dedicated");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and no engProdIds.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithDerivedSku() {
    // Given a marketing SKU that has a derived SKU,
    var sku = "RH00604F5";
    var expected = new Offering();
    expected.setSku(sku);
    // (For now, Derived SKU and Derived SKU children are included as child SKUs.)
    expected.setChildSkus(Arrays.asList("RH00618F5", "SVCRH00604", "SVCRH00618"));
    // (Neither the parent (as typical) nor the child SKU have eng products. These end up
    //  coming from the derived SKU RH00048.)
    expected.setProductIds(
        Arrays.asList(
            69, 70, 83, 84, 86, 91, 92, 93, 127, 176, 180, 182, 201, 205, 240, 241, 246, 248, 317,
            318, 394, 395, 408, 479, 491, 588));
    expected.setPhysicalSockets(2);
    expected.setVirtualSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL for SAP HANA");
    expected.setServiceLevel(ServiceLevel.PREMIUM);
    // (Usage ends up coming from derived SKU RH00618F5)
    expected.setUsage(Usage.PRODUCTION);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected virtual sockets from derived sku,
    // and engOIDs from the derived sku child.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithRoleAndUsage() {
    // This checks that role and usage are calculated correctly.

    // Given a marketing SKU that has a defined role and usage,
    var sku = "RH0180191";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Arrays.asList("SVCMPV4", "SVCRH01", "SVCRH01V4"));
    expected.setProductIds(
        Arrays.asList(
            69, 70, 84, 86, 91, 92, 93, 94, 127, 133, 176, 180, 182, 201, 205, 240, 246, 271, 272,
            273, 274, 317, 318, 394, 395, 408, 479, 491, 588, 605));
    expected.setRole("Red Hat Enterprise Linux Server");
    expected.setPhysicalCores(0);
    expected.setPhysicalSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL Server");
    expected.setServiceLevel(ServiceLevel.STANDARD);
    expected.setUsage(Usage.PRODUCTION);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, values, and engProdIds.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingWithIflAttrCode() {
    // Given a marketing SKU wiht attribute code "IFL" in its tree (in this case, in SVCMPV4)
    var sku = "RH3413336";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(
        Arrays.asList(
            "SVCEUSRH34", "SVCHPNRH34", "SVCMPV4", "SVCRH34", "SVCRH34V4", "SVCRS", "SVCSFS"));
    expected.setProductIds(
        Arrays.asList(
            68, 69, 70, 71, 83, 84, 85, 86, 90, 91, 92, 93, 132, 133, 172, 176, 179, 180, 190, 201,
            202, 203, 205, 206, 207, 240, 242, 244, 246, 273, 274, 287, 293, 317, 318, 342, 343,
            394, 395, 396, 397, 408, 479, 491, 588));
    expected.setPhysicalCores(4); // Because IFL is 1 which gets multiplied by magical constant 4
    expected.setPhysicalSockets(2);
    expected.setProductFamily("Red Hat Enterprise Linux");
    expected.setProductName("RHEL Developer Workstation");
    expected.setServiceLevel(ServiceLevel.EMPTY); // Because Dev-Enterprise isn't a ServiceLevel yet
    expected.setUsage(Usage.DEVELOPMENT_TEST);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has the expected child SKUs, engProd OIDs, and values.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithUnlimitedSockets() {
    // Given a marketing SKU that has unlimited number of sockets,
    var sku = "ESA0055";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Arrays.asList("SVCESA0055"));
    expected.setProductIds(
        Arrays.asList(
            69, 70, 84, 86, 91, 92, 93, 94, 127, 133, 176, 180, 182, 201, 205, 240, 246, 271, 272,
            273, 274, 317, 318, 394, 395, 408, 479, 491, 588, 605));
    expected.setProductFamily("RHEL");
    expected.setProductName("RHEL for SAP HANA");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has a physical sockets count of -1 to represent unlimited.
    assertEquals(expected, actual);
  }

  @Test
  void testGetUpstreamOfferingForOfferingWithUnlimitedCores() {
    // Given a marketing SKU that has unlimited number of cores,
    var sku = "ESA0052";
    var expected = new Offering();
    expected.setSku(sku);
    expected.setChildSkus(Arrays.asList("SVCESA0052"));
    expected.setProductIds(
        Arrays.asList(
            69, 70, 167, 180, 185, 193, 194, 197, 201, 205, 240, 271, 290, 303, 311, 317, 318, 326,
            329, 408, 458, 473, 479, 491, 518, 519, 546, 579, 588, 603, 604, 608));
    expected.setProductFamily("OpenShift Enterprise");
    expected.setProductName("OpenShift Node");
    expected.setServiceLevel(ServiceLevel.PREMIUM);

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku).orElseThrow();

    // Then the resulting Offering has a phsyical cores count of -1 to represent unlimited.
    assertEquals(expected, actual);
  }

  @Test()
  void testGetUpstreamOfferingNotInAllowlist() {
    // Given a marketing SKU not listed in allowlist,
    when(allowlist.productIdMatches(anyString())).thenReturn(false);
    var sku = "MW01485"; // The SKU would normally be successfully retrieved, but is denied

    // When getting the upstream Offering,
    var actual = subject.getUpstreamOffering(sku);

    // Then there is no resulting offering.
    assertTrue(actual.isEmpty(), "A sku not in the allowlist should not be returned.");
    verify(allowlist).productIdMatches(eq(sku));
  }

  @Test()
  void testGetUpstreamOfferingNotFound() {
    // Given a marketing SKU that doesn't exist upstream,
    var sku = "BOGUS";

    // When attempting to get the upstream Offering,
    var actual = subject.getUpstreamOffering(sku);

    // Then there is no resulting offering.
    assertTrue(actual.isEmpty(), "When a sku doesn't exist upstream, return an empty Optional.");
  }
}
