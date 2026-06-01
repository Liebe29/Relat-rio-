package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "report_templates")
data class ReportTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val fieldsJson: String // Serialized List<ReportField>
)

data class ReportField(
    val label: String,          // e.g., "Meta atingida?"
    val type: String,           // "TEXT" or "CHOICE"
    val options: List<String> = emptyList() // e.g., ["Sim", "Não"] for CHOICE; empty for TEXT
)

object JsonUtils {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun fieldsToJson(fields: List<ReportField>): String {
        val type = Types.newParameterizedType(List::class.java, ReportField::class.java)
        return moshi.adapter<List<ReportField>>(type).toJson(fields)
    }

    fun jsonToFields(json: String): List<ReportField> {
        return try {
            val type = Types.newParameterizedType(List::class.java, ReportField::class.java)
            moshi.adapter<List<ReportField>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun answersToJson(answers: Map<String, String>): String {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        return moshi.adapter<Map<String, String>>(type).toJson(answers)
    }

    fun jsonToAnswers(json: String): Map<String, String> {
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
