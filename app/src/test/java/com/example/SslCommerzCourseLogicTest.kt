package com.example

import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.CoursePurchaseStatusResult
import com.example.domain.model.CourseSummary
import com.example.domain.model.SslCommerzCheckoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SslCommerzCourseLogicTest {

    @Test
    fun resolveDisplayCoursePrice_enforcesDefaultMinimumFloor() {
        assertEquals(499.0, CourseDomainUtils.resolveDisplayCoursePrice(0.0), 0.001)
        assertEquals(499.0, CourseDomainUtils.resolveDisplayCoursePrice(9.99), 0.001)
        assertEquals(499.0, CourseDomainUtils.resolveDisplayCoursePrice(Double.NaN), 0.001)
        assertEquals(10.0, CourseDomainUtils.resolveDisplayCoursePrice(10.0), 0.001)
        assertEquals(1250.0, CourseDomainUtils.resolveDisplayCoursePrice(1250.0), 0.001)
    }

    @Test
    fun formatCoursePrice_formatsBdtAndOtherCurrenciesCleanly() {
        assertEquals("৳499", CourseDomainUtils.formatCoursePrice(0.0))
        assertEquals("৳990", CourseDomainUtils.formatCoursePrice(990.0))
        assertEquals("৳499.50", CourseDomainUtils.formatCoursePrice(499.5))
        assertEquals("USD 25", CourseDomainUtils.formatCoursePrice(25.0, "USD"))
    }

    @Test
    fun courseAndCheckoutModels_capturePurchaseAndUnlockState() {
        val lockedCourse = CourseSummary(
            id = "c1",
            title = "Business English Pro",
            isPremium = true,
            price = 799.0,
            hasAccess = false,
            isPurchased = false
        )
        assertFalse(lockedCourse.hasAccess)
        assertFalse(lockedCourse.isPurchased)
        assertTrue(CourseDomainUtils.isLessonLocked(lessonIsPremium = true, hasPremiumAccess = lockedCourse.hasAccess))

        val purchasedCourse = lockedCourse.copy(hasAccess = true, isPurchased = true)
        assertTrue(purchasedCourse.hasAccess)
        assertTrue(purchasedCourse.isPurchased)
        assertFalse(CourseDomainUtils.isLessonLocked(lessonIsPremium = true, hasPremiumAccess = purchasedCourse.hasAccess))

        val session = SslCommerzCheckoutSession(
            purchaseId = "pur_1",
            tranId = "SSL_TEST_1",
            courseId = "c1",
            courseTitle = "Business English Pro",
            amount = 799.0,
            currency = "BDT",
            gatewayUrl = "https://sandbox.sslcommerz.com/gwprocess/v4/gw.php",
            isSandbox = true
        )
        assertEquals("BDT", session.currency)
        assertTrue(session.isSandbox)

        val status = CoursePurchaseStatusResult(
            purchaseId = "pur_1",
            tranId = "SSL_TEST_1",
            courseId = "c1",
            status = "paid",
            amount = 799.0,
            currency = "BDT",
            cardType = "BKASH-BKash",
            bankTranId = "BTX99",
            hasAccess = true,
            message = "Payment verified!"
        )
        assertEquals("paid", status.status)
        assertTrue(status.hasAccess)
    }
}
