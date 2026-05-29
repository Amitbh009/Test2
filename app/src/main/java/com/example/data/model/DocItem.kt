package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "documents")
data class DocItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val contentJson: String, // Contains the JSON list of paragraphs for Word documents
    val updatedAt: Long = System.currentTimeMillis(),
    val type: String, // "WORD" (Word Document mode) or "PDF" (PDF Annotation mode)
    val pdfBytes: ByteArray? = null, // The binary contents of the PDF (or drawing cached preview)
    val annotationsJson: String? = null // Contains serialized drawing strokes, text notes, highlights for overlaying on PDF pages
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocItem
        if (id != other.id) return false
        if (title != other.title) return false
        if (contentJson != other.contentJson) return false
        if (updatedAt != other.updatedAt) return false
        if (type != other.type) return false
        if (annotationsJson != other.annotationsJson) return false
        if (pdfBytes != null) {
            if (other.pdfBytes == null) return false
            if (!pdfBytes.contentEquals(other.pdfBytes)) return false
        } else if (other.pdfBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + contentJson.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (pdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + (annotationsJson?.hashCode() ?: 0)
        return result
    }
}
