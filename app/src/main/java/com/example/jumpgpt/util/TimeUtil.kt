package com.example.jumpgpt.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun now(): Long {
        return System.currentTimeMillis()
    }

    fun formatTime(timestamp: Long): String {
        val context = applicationContext ?: throw IllegalStateException("TimeUtil not initialized. Call init() first.")
        return DateFormat.getTimeFormat(context).format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val context = applicationContext ?: throw IllegalStateException("TimeUtil not initialized. Call init() first.")
        val date = Date(timestamp)
        val dateFormat = DateFormat.getMediumDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    fun formatRelativeTime(timestamp: Long): String {
        val context = applicationContext ?: throw IllegalStateException("TimeUtil not initialized. Call init() first.")
        return DateUtils.getRelativeDateTimeString(
            context,
            timestamp,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0
        ).toString()
    }
} 