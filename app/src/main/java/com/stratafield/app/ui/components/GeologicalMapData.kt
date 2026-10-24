package com.stratafield.app.ui.components

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.stratafield.app.data.model.FormResponse

data class GeoPoint(val longitude: Double, val latitude: Double)
data class GeoFeature(
    val id: String,
    val kind: String,
    val name: String,
    val color: String,
    val points: List<GeoPoint>
)
data class SurveyMapPin(val responseId: Long, val name: String, val notes: String, val point: GeoPoint)

/** GeoJSON is the persisted source of truth; map screen pixels are never stored. */
object GeologicalMapData {
    private val gson = Gson()

    fun draftToGeoJson(points: List<GeoPoint>, kind: String, color: String = "#D84315"): String {
        val draftFeatures = JsonArray()
        fun addFeature(type: String, coordinates: JsonElement) {
            draftFeatures.add(JsonObject().apply {
                addProperty("type", "Feature")
                add("properties", JsonObject().apply { addProperty("color", color) })
                add("geometry", JsonObject().apply {
                    addProperty("type", type)
                    add("coordinates", coordinates)
                })
            })
        }
        fun coordinate(point: GeoPoint) = JsonArray().also { it.add(point.longitude); it.add(point.latitude) }

        points.forEach { addFeature("Point", coordinate(it)) }
        if (kind == "polygon" && points.size >= 3) {
            val ringPoints = points.toMutableList().apply { if (last() != first()) add(first()) }
            addFeature("Polygon", JsonArray().also { rings -> rings.add(JsonArray().also { ring -> ringPoints.forEach { ring.add(coordinate(it)) } }) })
        } else if (points.size >= 2) {
            addFeature("LineString", JsonArray().also { line -> points.forEach { line.add(coordinate(it)) } })
        }

        return gson.toJson(JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", draftFeatures)
        })
    }

    fun toGeoJson(features: List<GeoFeature>): String {
        val collection = JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", JsonArray().also { array -> features.forEach { f -> array.add(featureJson(f)) } })
        }
        return gson.toJson(collection)
    }

    fun fromGeoJson(json: String): List<GeoFeature> = runCatching {
        val features = JsonParser.parseString(json).asJsonObject.getAsJsonArray("features")
        features.mapNotNull { node ->
            val f = node.asJsonObject
            val p = f.getAsJsonObject("properties")
            val geometry = f.getAsJsonObject("geometry")
            val type = geometry.get("type").asString
            val coordinates = geometry.getAsJsonArray("coordinates")
            val raw = when (type) {
                "Point" -> listOf(coordinates)
                "LineString" -> coordinates.map { it.asJsonArray }
                "Polygon" -> coordinates.firstOrNull()?.asJsonArray?.map { it.asJsonArray }.orEmpty()
                else -> emptyList()
            }
            val parsedPoints = raw.mapNotNull { c ->
                if (c.size() < 2) null else GeoPoint(c[0].asDouble, c[1].asDouble)
            }
            val points = if (type == "Polygon" && parsedPoints.size > 1 && parsedPoints.first() == parsedPoints.last()) parsedPoints.dropLast(1) else parsedPoints
            if (points.isEmpty()) null else GeoFeature(
                id = p.get("id")?.asString ?: f.get("id")?.asString ?: "feature-${features.indexOf(node)}",
                kind = p.get("kind")?.asString ?: "point",
                name = p.get("name")?.asString ?: "",
                color = p.get("color")?.asString ?: "#D32F2F",
                points = points
            )
        }
    }.getOrDefault(emptyList())

    private fun featureJson(feature: GeoFeature): JsonObject {
        val geometryType = when (feature.kind) {
            "point", "text" -> "Point"
            "polygon" -> "Polygon"
            else -> "LineString"
        }
        val coords = feature.points.map { p -> JsonArray().also { it.add(p.longitude); it.add(p.latitude) } }.toMutableList()
        val geometryCoordinates = when (geometryType) {
            "Point" -> coords.firstOrNull() ?: JsonArray()
            "Polygon" -> JsonArray().also { rings ->
                val closed = coords.toMutableList()
                if (closed.size >= 3 && feature.points.last() != feature.points.first()) {
                    closed.add(JsonArray().also { it.add(feature.points.first().longitude); it.add(feature.points.first().latitude) })
                }
                rings.add(JsonArray().also { ring -> closed.forEach(ring::add) })
            }
            else -> JsonArray().also { array -> coords.forEach(array::add) }
        }
        return JsonObject().apply {
            addProperty("type", "Feature")
            addProperty("id", feature.id)
            add("properties", JsonObject().apply {
                addProperty("id", feature.id); addProperty("kind", feature.kind)
                addProperty("name", feature.name); addProperty("color", feature.color)
            })
            add("geometry", JsonObject().apply {
                addProperty("type", geometryType)
                add("coordinates", geometryCoordinates)
            })
        }
    }

    fun surveyPins(responses: List<FormResponse>): List<SurveyMapPin> = responses.mapNotNull { response ->
        val values = runCatching { JsonParser.parseString(response.dataJson).asJsonObject }.getOrNull() ?: return@mapNotNull null
        val location = values.entrySet().firstNotNullOfOrNull { (_, value) ->
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return@firstNotNullOfOrNull null
            runCatching { JsonParser.parseString(value.asString).asJsonObject }.getOrNull()
                ?.takeIf { it.has("latitude") && it.has("longitude") }
        } ?: return@mapNotNull null
        val lat = runCatching { location.get("latitude").asDouble }.getOrNull() ?: return@mapNotNull null
        val lon = runCatching { location.get("longitude").asDouble }.getOrNull() ?: return@mapNotNull null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
        val name = location.get("siteName")?.asString?.trim().orEmpty().ifBlank { response.formName }
        val notes = values.entrySet().firstOrNull { it.key.contains("notes", ignoreCase = true) }
            ?.value?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        SurveyMapPin(response.id, name, notes, GeoPoint(lon, lat))
    }
}
