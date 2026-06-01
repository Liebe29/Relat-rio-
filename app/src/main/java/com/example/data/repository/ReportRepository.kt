package com.example.data.repository

import com.example.data.db.ReportDao
import com.example.data.model.FilledReport
import com.example.data.model.ReportTemplate
import kotlinx.coroutines.flow.Flow

class ReportRepository(private val reportDao: ReportDao) {
    val allTemplates: Flow<List<ReportTemplate>> = reportDao.getAllTemplates()
    val allFilledReports: Flow<List<FilledReport>> = reportDao.getAllFilledReports()

    fun getFilledReportsByTemplate(templateId: Int): Flow<List<FilledReport>> {
        return reportDao.getFilledReportsByTemplate(templateId)
    }

    suspend fun getTemplateById(id: Int): ReportTemplate? {
        return reportDao.getTemplateById(id)
    }

    suspend fun insertTemplate(template: ReportTemplate): Long {
        return reportDao.insertTemplate(template)
    }

    suspend fun deleteTemplateById(id: Int) {
        reportDao.deleteTemplateById(id)
    }

    suspend fun getFilledReportById(id: Int): FilledReport? {
        return reportDao.getFilledReportById(id)
    }

    suspend fun insertFilledReport(report: FilledReport): Long {
        return reportDao.insertFilledReport(report)
    }

    suspend fun deleteFilledReportById(id: Int) {
        reportDao.deleteFilledReportById(id)
    }
}
