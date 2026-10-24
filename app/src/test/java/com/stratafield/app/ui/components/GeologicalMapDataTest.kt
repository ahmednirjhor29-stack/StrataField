package com.stratafield.app.ui.components

import com.stratafield.app.data.model.FormResponse
import com.stratafield.app.data.model.FormType
import com.stratafield.app.data.model.DataStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeologicalMapDataTest {
    @Test fun geoJsonRoundTripsCoordinatesAndFeatureStyle() {
        val original = listOf(GeoFeature("f1", "line", "Contact", "#336699", listOf(GeoPoint(-71.2, 42.3), GeoPoint(-71.1, 42.4))))
        val restored = GeologicalMapData.fromGeoJson(GeologicalMapData.toGeoJson(original))
        assertEquals(original, restored)
    }

    @Test fun savedSurveyLocationBecomesNamedPinWithGeneralNotes() {
        val response = FormResponse(
            id = 8, projectId = 3, formName = "Survey", formType = FormType.SURVEY,
            dataJson = """{"survey_location":"{\"siteName\":\"North Ridge\",\"latitude\":24.1,\"longitude\":90.2}","survey_notes":"Basalt outcrop"}""",
            status = DataStatus.DRAFT
        )
        val pin = GeologicalMapData.surveyPins(listOf(response)).single()
        assertEquals("North Ridge", pin.name)
        assertEquals("Basalt outcrop", pin.notes)
        assertEquals(90.2, pin.point.longitude, 0.0)
        assertEquals(24.1, pin.point.latitude, 0.0)
    }

    @Test fun malformedCoordinatesAreIgnored() {
        val response = FormResponse(id = 2, projectId = 1, formName = "bad", dataJson = "{}")
        assertTrue(GeologicalMapData.surveyPins(listOf(response)).isEmpty())
    }
}
