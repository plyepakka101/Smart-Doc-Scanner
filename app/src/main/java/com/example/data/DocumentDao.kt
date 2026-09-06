package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdTime DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY createdTime DESC")
    suspend fun getAllDocumentsList(): List<Document>

    @Query("SELECT * FROM documents WHERE id = :docId")
    suspend fun getDocumentById(docId: Long): Document?

    @Query("SELECT DISTINCT folder FROM documents WHERE folder IS NOT NULL AND folder != ''")
    suspend fun getDistinctFolders(): List<String>

    @Query("UPDATE documents SET folder = :newFolder WHERE id = :docId")
    suspend fun updateDocumentFolder(docId: Long, newFolder: String)

    @Query("UPDATE documents SET folder = :newFolder WHERE id IN (:docIds)")
    suspend fun updateBatchDocumentFolders(docIds: List<Long>, newFolder: String)

    @Query("UPDATE documents SET folder = :newFolder WHERE folder = :oldFolder")
    suspend fun renameFolderInDocuments(oldFolder: String, newFolder: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("SELECT * FROM scanned_pages WHERE documentId = :docId ORDER BY pageNumber ASC")
    fun getPagesForDocument(docId: Long): Flow<List<ScannedPage>>

    @Query("SELECT * FROM scanned_pages WHERE documentId = :docId ORDER BY pageNumber ASC")
    suspend fun getPagesListForDocument(docId: Long): List<ScannedPage>

    @Query("SELECT * FROM scanned_pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): ScannedPage?

    @Query("SELECT * FROM scanned_pages")
    suspend fun getAllPages(): List<ScannedPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: ScannedPage): Long

    @Update
    suspend fun updatePage(page: ScannedPage)

    @Delete
    suspend fun deletePage(page: ScannedPage)
}
