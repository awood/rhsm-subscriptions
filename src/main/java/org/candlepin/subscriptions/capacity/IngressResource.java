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
package org.candlepin.subscriptions.capacity;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.resources.IngressApi;
import org.springframework.stereotype.Component;

/** Updates subscription capacity based on Candlepin pool data. */
@Component
public class IngressResource implements IngressApi {

  private final PoolIngressController controller;

  public IngressResource(PoolIngressController controller) {
    this.controller = controller;
  }

  @SuppressWarnings("java:S125")
  @Override
  public void updateCapacityFromCandlepinPools(
      String orgId, @Valid @NotNull List<CandlepinPool> pools) {
    controller.updateCapacityForOrg(orgId, pools);

    // Card to address this: ENT-3573
    // controller.updateSubscriptionsForOrg(orgId, pools);
  }
}
