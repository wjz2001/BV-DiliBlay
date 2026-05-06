package dev.aaa1115910.biliapi.http.entity.cheese

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CheesePaidData(
    @SerialName("data")
    val items: List<JsonObject> = emptyList(),
    val next: Boolean = false,
    val total: Int = 0,
    val pn: Int = 1,
    val ps: Int = 20
)
