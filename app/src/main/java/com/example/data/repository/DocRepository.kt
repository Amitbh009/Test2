package com.example.data.repository

import com.example.data.db.DocDao
import com.example.data.model.DocItem
import kotlinx.coroutines.flow.Flow

class DocRepository(private val docDao: DocDao) {
    val allDocuments: Flow<List<DocItem>> = docDao.getAllDocuments()

    suspend fun getDocumentById(id: Int): DocItem? {
        return docDao.getDocumentById(id)
    }

    suspend fun insertDocument(doc: DocItem): Long {
        return docDao.insertDocument(doc)
    }

    suspend fun updateDocument(doc: DocItem) {
        docDao.updateDocument(doc)
    }

    suspend fun deleteDocument(doc: DocItem) {
        docDao.deleteDocument(doc)
    }

    suspend fun deleteDocumentById(id: Int) {
        docDao.deleteDocumentById(id)
    }
}
