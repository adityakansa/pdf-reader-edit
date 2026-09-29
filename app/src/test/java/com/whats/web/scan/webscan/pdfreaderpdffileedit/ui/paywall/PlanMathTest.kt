package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanMathTest {
    @Test fun periods_in_days() {
        assertEquals(365, PlanMath.days("P1Y"))
        assertEquals(30, PlanMath.days("P1M"))
        assertEquals(21, PlanMath.days("P3W"))
        assertEquals(0, PlanMath.days("bad"))
    }

    @Test fun yearly_saving_against_monthly() {
        // ₹4,150 a year against ₹1,350 a month: 74 % cheaper per day, as the reference screen says.
        assertEquals(74, PlanMath.savingPercent(4_150_000_000, "P1Y", 1_350_000_000, "P1M"))
        assertEquals(0, PlanMath.savingPercent(20_000_000_000, "P1Y", 1_000_000, "P1M"))
    }

    @Test fun per_day_price() {
        assertEquals(true, PlanMath.perDay(4_150_000_000, 365, "INR")!!.contains("11.37"))
        assertNull(PlanMath.perDay(0, 365, "INR"))
    }
}
