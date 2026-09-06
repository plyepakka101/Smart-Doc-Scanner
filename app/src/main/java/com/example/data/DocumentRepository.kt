package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(val documentDao: DocumentDao) {
    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()

    suspend fun getAllDocumentsList(): List<Document> {
        return documentDao.getAllDocumentsList()
    }

    suspend fun getDocumentById(docId: Long): Document? {
        return documentDao.getDocumentById(docId)
    }

    suspend fun getDistinctFolders(): List<String> {
        return documentDao.getDistinctFolders()
    }

    suspend fun updateDocumentFolder(docId: Long, newFolder: String) {
        documentDao.updateDocumentFolder(docId, newFolder)
    }

    suspend fun updateBatchDocumentFolders(docIds: List<Long>, newFolder: String) {
        documentDao.updateBatchDocumentFolders(docIds, newFolder)
    }

    suspend fun renameFolderInDocuments(oldFolder: String, newFolder: String) {
        documentDao.renameFolderInDocuments(oldFolder, newFolder)
    }

    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document)
    }

    fun getPagesForDocument(docId: Long): Flow<List<ScannedPage>> {
        return documentDao.getPagesForDocument(docId)
    }

    suspend fun getPagesListForDocument(docId: Long): List<ScannedPage> {
        return documentDao.getPagesListForDocument(docId)
    }

    suspend fun getPageById(pageId: Long): ScannedPage? {
        return documentDao.getPageById(pageId)
    }

    suspend fun getAllPages(): List<ScannedPage> {
        return documentDao.getAllPages()
    }

    suspend fun insertPage(page: ScannedPage): Long {
        return documentDao.insertPage(page)
    }

    suspend fun updatePage(page: ScannedPage) {
        documentDao.updatePage(page)
    }

    suspend fun deletePage(page: ScannedPage) {
        documentDao.deletePage(page)
    }
}
