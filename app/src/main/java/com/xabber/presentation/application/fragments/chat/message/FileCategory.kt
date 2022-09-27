package com.xabber.presentation.application.fragments.chat.message

enum class FileCategory {
    IMAGE, AUDIO, VIDEO, DOCUMENT, PDF, TABLE, PRESENTATION, ARCHIVE, FILE;


    companion object {
        fun determineFileCategory(mimeType: String?): FileCategory {
            if (mimeType == null) return FILE
            return if (mimeType.contains("image/") || mimeType.contains("IMG")) {
                IMAGE
            } else if (mimeType.contains("audio/")) {
                AUDIO
            } else if (mimeType.contains("video/")) {
                VIDEO
            } else if (mimeType.contains("text/") || mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/vnd.oasis.opendocument.text" || mimeType == "application/vnd.oasis.opendocument.graphics" || mimeType == "application/msword") {
                DOCUMENT
            } else if (mimeType == "application/pdf") {
                PDF
            } else if (mimeType == "application/vnd.oasis.opendocument.spreadsheet" || mimeType == "application/vnd.ms-excel") {
                TABLE
            } else if (mimeType == "application/vnd.ms-powerpoint" || mimeType == "application/vnd.oasis.opendocument.presentation") {
                PRESENTATION
            } else if (mimeType == "application/zip" || mimeType == "application/gzip" || mimeType == "application/x-rar-compressed" || mimeType == "application/x-tar" || mimeType == "application/x-7z-compressed") {
                ARCHIVE
            } else {
                FILE
            }
        }

        fun getCategoryName(category: FileCategory?, withHtml: Boolean): String {
            return when (category) {
                IMAGE -> if (withHtml) "<font color='#1565c0'>Image:</font> " else "Image: "
                AUDIO -> if (withHtml) "<font color='#1565c0'>Audio:</font> " else "Audio: "
                VIDEO -> if (withHtml) "<font color='#1565c0'>Video:</font> " else "Video: "
                DOCUMENT -> if (withHtml) "<font color='#1565c0'>Document:</font> " else "Document: "
                PDF -> if (withHtml) "<font color='#1565c0'>PDF:</font> " else "PDF: "
                TABLE -> if (withHtml) "<font color='#1565c0'>Table:</font> " else "Table: "
                PRESENTATION -> if (withHtml) "<font color='#1565c0'>Presentation:</font> " else "Presentation: "
                ARCHIVE -> if (withHtml) "<font color='#1565c0'>Archive:</font> " else "Archive: "
                else -> if (withHtml) "<font color='#1565c0'>File:</font> " else "File: "
            }
        }
    }
}