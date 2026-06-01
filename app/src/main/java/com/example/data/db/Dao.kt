package com.example.data.db

import androidx.room.*
import com.example.data.model.FilledReport
import com.example.data.model.ReportTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    
    // Report Templates Queries
    @Query("SELECT * FROM report_templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<ReportTemplate>>

    @Query("SELECT * FROM report_templates WHERE id = :id")
    suspend fun getTemplateById(id: Int): ReportTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ReportTemplate): Long

    @Query("DELETE FROM report_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Int)

    // Filled Reports Queries
    @Query("SELECT * FROM filled_reports ORDER BY filledAt DESC")
    fun getAllFilledReports(): Flow<List<FilledReport>>

    @Query("SELECT * FROM filled_reports WHERE templateId = :templateId ORDER BY filledAt DESC")
    fun getFilledReportsByTemplate(templateId: Int): Flow<List<FilledReport>>

    @Query("SELECT * FROM filled_reports WHERE id = :id")
    suspend fun getFilledReportById(id: Int): FilledReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilledReport(report: FilledReport): Long

    @Query("DELETE FROM filled_reports WHERE id = :id")
    suspend fun deleteFilledReportById(id: Int)
}
