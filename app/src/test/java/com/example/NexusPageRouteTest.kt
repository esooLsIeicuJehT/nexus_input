package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.nexusAccentForRoute
import com.example.ui.theme.nexusMainTabForRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Protects the approved page accent/route ownership contract, independently of device readiness. */
class NexusPageRouteTest {
    @Test fun secondaryScreensShareTheCorrectMappingOnlyParent() {
        val ownedRoutes = mapOf(
            "home" to listOf("home"),
            "profiles" to listOf("profiles", "library", "profile_detail"),
            "mapper" to listOf("mapper", "macro"),
            "devices" to listOf("devices", "calibration"),
            "system" to listOf("system", "root_webui", "fps", "crosshair", "profile_files", "safety")
        )
        ownedRoutes.forEach { (parent, children) ->
            children.forEach { route -> assertEquals("Route $route", parent, nexusMainTabForRoute(route)) }
        }
    }

    @Test fun canonicalPageAccentsStayStableForTheirSecondaryRoutes() {
        val referenceAccents = mapOf(
            Color(0xFF16F1FF) to listOf("home"),
            Color(0xFFC05CFF) to listOf("profiles", "library", "profile_detail"),
            Color(0xFFEC5DCE) to listOf("mapper", "macro"),
            Color(0xFF19F2A0) to listOf("devices", "calibration"),
            Color(0xFFFFA726) to listOf("system", "root_webui", "fps", "crosshair", "profile_files", "safety")
        )
        referenceAccents.forEach { (referenceColor, routes) ->
            routes.forEach { route -> assertEquals("Approved accent for $route", referenceColor, nexusAccentForRoute(route)) }
        }
    }

    @Test fun unknownOrRemovedScreensNeverAcquireAProductionNavigationParent() {
        listOf("", "unknown", "community", "vip", "performance", "cpu", "gpu", "thermal").forEach { route ->
            assertNull("Removed/unknown route $route", nexusMainTabForRoute(route))
            // This is a visual default only; it grants no route or backend capability.
            assertEquals(Color(0xFF16F1FF), nexusAccentForRoute(route))
        }
    }
}
