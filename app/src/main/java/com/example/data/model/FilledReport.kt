package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filled_reports")
data class FilledReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateId: Int,
    val templateTitle: String,
    val respondentName: String,
    val respondentPhone: String,
    val filledAt: Long = System.currentTimeMillis(),
    val answersJson: String // Serialized Map<String, String>: FieldLabel -> Answer Value
)
