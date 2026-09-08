package com.contenedores.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingTest {
    private val tiers = listOf(
        TramoTarifaEntity(desdeServicio=1,hastaServicio=130,importeCentimos=400),
        TramoTarifaEntity(desdeServicio=131,hastaServicio=150,importeCentimos=1500),
        TramoTarifaEntity(desdeServicio=151,hastaServicio=180,importeCentimos=2000)
    )
    @Test fun boundaries() {
        assertEquals(0L, billingTotal(0, tiers))
        assertEquals(400L, billingTotal(1, tiers))
        assertEquals(52000L, billingTotal(130, tiers))
        assertEquals(53500L, billingTotal(131, tiers))
        assertEquals(82000L, billingTotal(150, tiers))
        assertEquals(84000L, billingTotal(151, tiers))
        assertEquals(102000L, billingTotal(160, tiers))
        assertEquals(128000L, billingTotal(173, tiers))
        assertEquals(142000L, billingTotal(180, tiers))
        assertNull(tariffFor(181, tiers))
    }
    @Test fun periodBoundaries() {
        assertEquals("2026-08-16", billingPeriod(java.time.LocalDate.parse("2026-09-15")).first.toString())
        assertEquals("2026-09-16", billingPeriod(java.time.LocalDate.parse("2026-09-16")).first.toString())
    }
}
