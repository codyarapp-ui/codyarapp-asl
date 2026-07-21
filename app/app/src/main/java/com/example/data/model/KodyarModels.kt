package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Json
@JsonClass(generateAdapter = true)
data class KodyarDatabaseResponse(
    val errorCodes: List<KodyarErrorCode>?,
    val spareParts: List<KodyarSparePart>?,
    val technicians: List<KodyarTechnician>?,
    val commonProblems: List<KodyarCommonProblem>?,
    val categoriesList: List<String>?,
    val brandsList: List<String>?,
    val citiesList: List<KodyarCity>?
)

@JsonClass(generateAdapter = true)
data class KodyarCity(
    val name: String? = null,
    val title: String? = null,
    val city: String? = null,
    val cityName: String? = null,
    val name_fa: String? = null,
    val nameFarsi: String? = null,
    val slug: String? = null
)

class KodyarCityAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): KodyarCity? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (reader.peek() == JsonReader.Token.STRING) {
            val nameValue = reader.nextString()
            return KodyarCity(name = nameValue)
        }
        reader.beginObject()
        var name: String? = null
        var title: String? = null
        var city: String? = null
        var cityName: String? = null
        var name_fa: String? = null
        var nameFarsi: String? = null
        var slug: String? = null

        val options = JsonReader.Options.of("name", "title", "city", "cityName", "name_fa", "nameFarsi", "slug")
        while (reader.hasNext()) {
            when (reader.selectName(options)) {
                0 -> name = reader.nextString()
                1 -> title = reader.nextString()
                2 -> city = reader.nextString()
                3 -> cityName = reader.nextString()
                4 -> name_fa = reader.nextString()
                5 -> nameFarsi = reader.nextString()
                6 -> slug = reader.nextString()
                else -> {
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        return KodyarCity(name, title, city, cityName, name_fa, nameFarsi, slug)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: KodyarCity?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        if (value.name != null) writer.name("name").value(value.name)
        if (value.title != null) writer.name("title").value(value.title)
        if (value.city != null) writer.name("city").value(value.city)
        if (value.cityName != null) writer.name("cityName").value(value.cityName)
        if (value.name_fa != null) writer.name("name_fa").value(value.name_fa)
        if (value.nameFarsi != null) writer.name("nameFarsi").value(value.nameFarsi)
        if (value.slug != null) writer.name("slug").value(value.slug)
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
data class KodyarErrorCode(
    val id: String?,
    val code: String?,
    val brand: String?,
    val category: String?,
    val title: String?,
    val description: String?,
    val causes: Any?, // Can be List<String> or String
    val steps: Any?, // Can be List<String> or String
    val hazardLevel: String?,
    val videoUrl: String?,
    val isApproved: Boolean?,
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarSparePart(
    val id: String?,
    val name: String?,
    val brand: String?,
    val price: Double?,
    val stock: Int?,
    val image: String?,
    val imageUrl: String?
)

@JsonClass(generateAdapter = true)
data class KodyarTechnician(
    val id: String?,
    val name: String?,
    @Json(name = "activeLocation") val city: String?,
    val isVerified: Boolean?,
    val completedOrders: Int?,
    val bio: String?,
    @Json(name = "specialty") val categories: List<String>?,
    val rating: Double? = 5.0,
    val satisfactionRate: Int? = 100,
    val image: String? = null,
    val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarCommonProblem(
    val id: String?,
    val title: String?,
    val brand: String?,
    val category: String?,
    val description: String?,
    val causes: Any?,
    val steps: Any?
)

@JsonClass(generateAdapter = true)
data class KodyarUser(
    val id: String,
    val full_name: String,
    val phone: String,
    val subscription: KodyarSubscription?,
    val role: String? = "customer",
    val city: String? = null,
    val categories: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarSubscription(
    val is_premium: Boolean,
    val expiry_date: String?
)

@JsonClass(generateAdapter = true)
data class KodyarResponse(
    val status: String,
    val user: KodyarUser?,
    val error: String?,
    val repairs: List<KodyarRepairOrder>?,
    val data: List<KodyarRepairOrder>?,
    val error_count: Int?,
    val problem_count: Int?
)

@JsonClass(generateAdapter = true)
data class KodyarRepairOrder(
    val id: String?,
    val technician_id: String?,
    val technician_name: String?,
    val description: String?,
    val city: String?,
    val status: String?, // pending, accepted, ongoing, completed, rejected
    val created_at: String?
)

@JsonClass(generateAdapter = true)
data class KodyarSubscriptionPlan(
    val id: String,
    val name: String,
    val duration_days: Int,
    val price: Double,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarPlansResponse(
    val status: String,
    val plans: List<KodyarSubscriptionPlan>?
)

@JsonClass(generateAdapter = true)
data class PartPurchaseOrder(
    val id: String,
    val partId: String,
    val partName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val address: String,
    val notes: String,
    val dateStr: String,
    val status: String // pending, sent, delivered
)

