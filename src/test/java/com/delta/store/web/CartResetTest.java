package com.delta.store.web;

import org.junit.jupiter.api.Test;

class CartResetTest {
    @Test void emptyCartResetsCheckoutSelections() {
        CartResetChecks.main(new String[0]);
    }
}
