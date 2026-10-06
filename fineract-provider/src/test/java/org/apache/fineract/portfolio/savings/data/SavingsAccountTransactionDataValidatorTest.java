/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.service.BackdatedTransactionValidationService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

/**
 * Transaction amounts are stored as numeric(19,6). A larger amount used to reach the database and was answered with
 * HTTP 403 and the raw SQL error "numeric field overflow"; it must be a normal validation error (400) instead.
 */
class SavingsAccountTransactionDataValidatorTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private final SavingsAccountTransactionDataValidator underTest = new SavingsAccountTransactionDataValidator(fromJsonHelper,
            mock(ConfigurationDomainService.class), mock(BackdatedTransactionValidationService.class));

    private JsonCommand command(String amount) {
        String json = "{\"transactionDate\":\"05 October 2026\",\"transactionAmount\":" + amount
                + ",\"paymentTypeId\":4,\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        return JsonCommand.from(json, fromJsonHelper.parse(json), fromJsonHelper, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void ordinaryAmountIsAccepted() {
        assertThatCode(() -> underTest.validate(command("100.50"))).doesNotThrowAnyException();
    }

    @Test
    void largestStorableAmountIsAccepted() {
        assertThatCode(() -> underTest.validate(command("9999999999999"))).doesNotThrowAnyException();
    }

    @Test
    void amountThatOverflowsTheStorageIsAValidationError() {
        assertThatThrownBy(() -> underTest.validate(command("1000000000000000"))).isInstanceOfSatisfying(
                PlatformApiDataValidationException.class, e -> assertThat(e.getErrors().get(0).getUserMessageGlobalisationCode())
                        .isEqualTo("validation.msg.savingsaccount.transaction.transactionAmount.is.greater.than.max"));
    }
}
