package com.delta.store.shipping;

import com.delta.store.shipping.model.ShippingCost;
import com.delta.store.shipping.repository.ShippingCostRepository;
import com.delta.store.shipping.service.ShippingServiceImpl;
import com.delta.store.shipping.service.ShippingService;
import com.delta.store.web.admin.AdminShippingCostController;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShippingManagementTest {
    private ShippingCost rule(String min, String max, String cost) {
        ShippingCost rule = new ShippingCost();
        rule.setMinTotal(new BigDecimal(min));
        rule.setMaxTotal(max == null ? null : new BigDecimal(max));
        rule.setCost(new BigDecimal(cost));
        return rule;
    }

    @Test
    void emptyCartDoesNotQueryRates() {
        ShippingCostRepository repository = mock(ShippingCostRepository.class);
        ShippingServiceImpl service = new ShippingServiceImpl(repository);
        assertThat(service.calculateShipping(null)).isEqualByComparingTo("0");
        assertThat(service.calculateShipping(BigDecimal.ZERO)).isEqualByComparingTo("0");
        verifyNoInteractions(repository);
    }

    @Test
    void usesConfiguredRateAndPreservesNoMatchFallback() {
        ShippingCostRepository repository = mock(ShippingCostRepository.class);
        ShippingServiceImpl service = new ShippingServiceImpl(repository);
        when(repository.findBestMatch(new BigDecimal("49.99")))
                .thenReturn(Optional.of(rule("0", "49.99", "5.90")));
        when(repository.findBestMatch(new BigDecimal("50"))).thenReturn(Optional.empty());
        assertThat(service.calculateShipping(new BigDecimal("49.99"))).isEqualByComparingTo("5.90");
        assertThat(service.calculateShipping(new BigDecimal("50"))).isEqualByComparingTo("0");
    }

    @Test
    void checksInclusiveBoundariesAndInactiveRules() {
        ShippingCostRepository repository = mock(ShippingCostRepository.class);
        ShippingServiceImpl service = new ShippingServiceImpl(repository);
        ShippingCost rule = rule("0", "49.99", "5.90");
        when(repository.findAllByOrderByMinTotalAscMaxTotalAscIdAsc()).thenReturn(List.of(rule));
        assertThat(service.hasActiveOverlap(null, new BigDecimal("49.99"), null)).isTrue();
        assertThat(service.hasActiveOverlap(null, new BigDecimal("50"), null)).isFalse();
        rule.setActive(false);
        assertThat(service.hasActiveOverlap(null, BigDecimal.ZERO, null)).isFalse();
    }

    @Test
    void invalidIntervalIsNotSaved() {
        ShippingService service = mock(ShippingService.class);
        AdminShippingCostController controller = new AdminShippingCostController(service);
        ShippingCost rule = rule("50", "10", "5.90");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(rule, "shippingCost");
        assertThat(controller.create(rule, errors, new ExtendedModelMap(),
                new RedirectAttributesModelMap())).isEqualTo("admin/shipping-costs/form");
        assertThat(errors.hasFieldErrors("maxTotal")).isTrue();
        verify(service, never()).save(any());
    }

    @Test
    void overlappingActiveRateIsNotSaved() {
        ShippingService service = mock(ShippingService.class);
        when(service.hasActiveOverlap(null, BigDecimal.ZERO, null)).thenReturn(true);
        AdminShippingCostController controller = new AdminShippingCostController(service);
        ShippingCost rule = rule("0", null, "5.90");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(rule, "shippingCost");
        controller.create(rule, errors, new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertThat(errors.hasGlobalErrors()).isTrue();
        verify(service, never()).save(any());
    }
}
