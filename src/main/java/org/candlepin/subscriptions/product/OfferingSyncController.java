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

import java.util.Objects;
import java.util.Optional;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Update {@link Offering}s from product service responses. */
@Component
public class OfferingSyncController {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfferingSyncController.class);

  private final OfferingRepository offeringRepository;

  public OfferingSyncController(OfferingRepository offeringRepository) {
    this.offeringRepository = offeringRepository;
  }

  /**
   * Persists the latest state of an Offering. If no stored Offering matches the SKU, then the
   * Offering is inserted into the datastore. Otherwise, if there are actual changes, the stored
   * Offering with the matching SKU is updated with the given Offering.
   *
   * @param newState the updated Offering
   */
  public void syncOffering(Offering newState) {
    Optional<Offering> persistedOffering = offeringRepository.findById(newState.getSku());
    if (alreadySynced(persistedOffering, newState)) {
      LOGGER.debug(
          "The given sku=\"{}\" is equal to stored sku. Skipping sync.",
          persistedOffering.get().getSku());
      return;
    }

    // Update to the new entry or create it.
    offeringRepository.save(newState);

    // TODO ENT-2658 mentions calling TaskManager.reconcileCapacityForOffering if there //NOSONAR
    // are changes, but this doesn't exist.
    // taskManager.reconcileCapacityForOffering(...); //NOSONAR
  }

  // If there is an existing offering in the DB, and it exactly matches the latest upstream
  // version then return true. False means we should sync with the latest upstream version.
  private boolean alreadySynced(Optional<Offering> persisted, Offering latest) {
    return persisted.isPresent() && Objects.equals(persisted.get(), latest);
  }
}
