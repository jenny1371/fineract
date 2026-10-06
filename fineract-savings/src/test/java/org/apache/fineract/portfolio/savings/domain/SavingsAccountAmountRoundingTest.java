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
package org.apache.fineract.portfolio.savings.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An amount such as 0.005 is positive, so it passes the request validation, but it is rounded to the currency's decimal
 * places when the money is created. Without a check a deposit of 0.005 answered 200 and posted a transaction of 0.00.
 */
class SavingsAccountAmountRoundingTest {

    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        MoneyHelper.clearCache();
        MoneyHelper.initializeTenantRoundingMode("default", 6); // HALF_EVEN, the default of a tenant: 0.005 -> 0.00
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MoneyHelper.clearCache();
    }

    private SavingsAccount account() {
        SavingsAccount account = new SavingsAccount() {};
        setCurrency(account);
        return account;
    }

    private SavingsAccountTransactionDTO dto(String amount) {
        SavingsAccountTransactionDTO dto = mock(SavingsAccountTransactionDTO.class);
        when(dto.getTransactionAmount()).thenReturn(new BigDecimal(amount));
        return dto;
    }

    @Test
    void amountThatRoundsToZeroIsRejected() {
        SavingsAccount account = account();

        assertThatThrownBy(
                () -> account.validateAmountIsNotZeroAfterRounding(Money.of(USD, new BigDecimal("0.005")), dto("0.005"), "savingsaccount"))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void amountThatRoundsToZeroCarriesAClearErrorCode() {
        SavingsAccount account = account();

        assertThatThrownBy(
                () -> account.validateAmountIsNotZeroAfterRounding(Money.of(USD, new BigDecimal("0.004")), dto("0.004"), "savingsaccount"))
                .isInstanceOfSatisfying(PlatformApiDataValidationException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getErrors()).hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(e.getErrors().get(0).getUserMessageGlobalisationCode())
                            .isEqualTo("error.msg.savingsaccount.transaction.amount.is.zero.after.rounding");
                });
    }

    @Test
    void smallestRepresentableAmountAndOrdinaryAmountsAreAccepted() {
        SavingsAccount account = account();

        assertThatCode(() -> {
            account.validateAmountIsNotZeroAfterRounding(Money.of(USD, new BigDecimal("0.01")), dto("0.01"), "savingsaccount");
            account.validateAmountIsNotZeroAfterRounding(Money.of(USD, new BigDecimal("0.1")), dto("0.1"), "savingsaccount");
            account.validateAmountIsNotZeroAfterRounding(Money.of(USD, new BigDecimal("1234.56")), dto("1234.56"), "savingsaccount");
        }).doesNotThrowAnyException();
    }

    private static void setCurrency(final SavingsAccount target) {
        try {
            final Field field = SavingsAccount.class.getDeclaredField("currency");
            field.setAccessible(true);
            field.set(target, USD);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set currency", e);
        }
    }
}
