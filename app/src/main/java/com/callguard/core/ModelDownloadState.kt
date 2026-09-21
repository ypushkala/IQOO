package com.callguard.core

/** What the import screen shows about a running (or failed) model download. */
data class ModelDownloadState(val active: Boolean, val percent: Int, val failedFiles: Int = 0, val doneFiles: Int = 0)
