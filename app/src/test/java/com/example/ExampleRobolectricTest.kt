package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.TransferDirection
import com.example.data.model.TransferStatus
import com.example.engine.ChecksumUtil
import com.example.engine.FileUtils
import com.example.ui.components.QrCodeGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FileSend Private", appName)
    }

    @Test
    fun `test streaming sha256 checksum calculation`() = runBlocking {
        val sampleData = "Hello FileSend Private! High speed secure transfers.".toByteArray()
        val inputStream = ByteArrayInputStream(sampleData)
        val hash = ChecksumUtil.calculateSha256(inputStream, sampleData.size.toLong())
        
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `test file utils formatting and sanitization`() {
        assertEquals("10 KB", FileUtils.formatFileSize(10240))
        assertEquals("1 MB", FileUtils.formatFileSize(1048576))
        assertEquals("1 GB", FileUtils.formatFileSize(1073741824))

        assertEquals(".apk", FileUtils.getFileExtension("my_app.apk"))
        assertEquals(".zip", FileUtils.getFileExtension("archive.tar.zip"))
        assertEquals("", FileUtils.getFileExtension("README"))

        assertEquals("safe_file_.txt", FileUtils.sanitizeFilename("../../safe:file?.txt"))
    }

    @Test
    fun `test qr code matrix generator`() {
        val matrix = QrCodeGenerator.generateQrMatrix("https://YOUR_DOMAIN/f/X7K92P")
        assertNotNull(matrix)
        assertTrue(matrix.isNotEmpty())
        assertEquals(25, matrix.size)
    }

    @Test
    fun `test room transfer history insert and retrieve`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.transferHistoryDao()

        val entity = TransferHistoryEntity(
            code = "X7K92P",
            fileName = "release.apk",
            fileExtension = ".apk",
            mimeType = "application/vnd.android.package-archive",
            fileSize = 10485760L,
            direction = TransferDirection.SEND,
            checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            status = TransferStatus.COMPLETED
        )

        val id = dao.insert(entity)
        assertTrue(id > 0)

        val retrieved = dao.getByCode("X7K92P")
        assertNotNull(retrieved)
        assertEquals("release.apk", retrieved?.fileName)
        assertEquals(TransferStatus.COMPLETED, retrieved?.status)

        val allList = dao.getAllHistory().first()
        assertEquals(1, allList.size)

        db.close()
    }

    @Test
    fun `test functional local relay health check`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.AppPreferences(context)
        val apiClient = com.example.data.remote.ApiClient(context, prefs)
        val service = apiClient.getService()

        val response = service.checkHealth()
        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals("ok", body?.status)
        assertTrue(body?.service?.contains("Relay") == true)
        assertTrue(body?.privateMode == true)
    }

    @Test
    fun `test cloud relay metadata publish and fetch`() {
        val testCode = "RB" + (1000..9999).random()
        val testName = "document_shared.pdf"
        val testSize = 1048576L
        val testMime = "application/pdf"
        val testChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val testDownloadUrl = "https://litter.catbox.moe/test_download.pdf"

        com.example.data.remote.CloudRelayBridge.publishMetadata(
            code = testCode,
            originalName = testName,
            size = testSize,
            mimeType = testMime,
            checksum = testChecksum,
            downloadUrl = testDownloadUrl,
            expiresAt = System.currentTimeMillis() + 86400000L
        )

        Thread.sleep(1000)

        val fetched = com.example.data.remote.CloudRelayBridge.fetchMetadata(testCode)
        assertNotNull(fetched)
        assertEquals(testCode, fetched?.getString("code"))
        assertEquals(testName, fetched?.getString("originalName"))
        assertEquals(testSize, fetched?.getLong("size"))
        assertEquals(testDownloadUrl, fetched?.getString("downloadUrl"))
    }
}
