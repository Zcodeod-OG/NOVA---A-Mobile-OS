package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.DocumentExtractionResult
import com.nova.runtime.ai.model.DocumentTextExtractor
import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.HashEmbeddingGenerator
import com.nova.runtime.ai.model.HashImageEmbeddingGenerator
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.ingestion.PhotoImageLoader
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.ContentExtractStatus
import com.nova.runtime.storage.search.SearchRequest
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end coverage for PDF/document content indexing and date-intelligent retrieval:
 * "send todays mess menu" must surface the menu document with today's section as plain text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DocumentContentSearchTest {
    private lateinit var vectorIndex: CosineVectorIndex
    private lateinit var embeddingGenerator: RecordingEmbeddingGenerator
    private lateinit var documentRepository: FakeDocumentRepository
    private lateinit var photoRepository: FakePhotoRepository
    private lateinit var pipeline: SearchIndexPipeline
    private lateinit var service: SemanticSearchService

    private val menuContent = buildString {
        appendLine("WEEKLY MESS MENU")
        java.time.DayOfWeek.entries.forEach { day ->
            appendLine(day.name)
            appendLine("Breakfast: breakfast-${day.name.lowercase()}")
            appendLine("Lunch: lunch-${day.name.lowercase()}")
        }
    }

    @Before
    fun setUp() {
        vectorIndex = CosineVectorIndex()
        embeddingGenerator = RecordingEmbeddingGenerator(HashEmbeddingGenerator(dimension = 32))
        documentRepository = FakeDocumentRepository()
        photoRepository = FakePhotoRepository()
        val indexer = EmbeddingIndexer(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
            embeddingRepository = FakeEmbeddingRepository(),
            vectorIndex = vectorIndex,
            ocrEngine = StubOcrEngine(),
        )
        pipeline = SearchIndexPipeline(
            embeddingIndexer = indexer,
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            photoImageLoader = PhotoImageLoader { null },
            documentTextExtractor = FakeDocumentTextExtractor(mapOf("content://docs/menu" to menuContent)),
        )
        val ingestionService = MediaStoreIngestionService(
            mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
            downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
            documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            embeddingIndexer = indexer,
            searchIndexPipeline = pipeline,
            photoImageLoader = PhotoImageLoader { null },
            logger = NoOpRuntimeLogger(),
        )
        service = SemanticSearchService(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
            vectorIndex = vectorIndex,
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            documentDao = FakeDocumentDao(documentRepository),
            searchIndexPipeline = pipeline,
            mediaStoreIngestionService = ingestionService,
            fullDeviceIndexer = null,
            logger = NoOpRuntimeLogger(),
        )
    }

    @Test
    fun indexDocument_extractsAndStoresContent_andEmbedsIt() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document

            pipeline.indexDocument(document)

            val stored = documentRepository.records[document.id]
            assertNotNull(stored)
            assertEquals(menuContent.trim(), stored!!.contentText?.trim())
            assertEquals(ContentExtractStatus.SUCCESS, stored.contentExtractStatus)
            assertNotNull(stored.contentExtractedAt)
            assertNotNull(stored.summary)
            assertTrue(stored.summary!!.contains("mess-menu.pdf"))
            assertTrue(stored.summary!!.contains("WEEKLY MESS MENU"))
            assertNotNull(stored.embeddingId)
            val indexedText = embeddingGenerator.lastEmbeddedText.orEmpty()
            assertTrue(indexedText.contains("mess-menu.pdf"))
            assertTrue(indexedText.contains("WEEKLY MESS MENU"))
            // Discovery embedding must stay short — not the full content body.
            assertTrue(indexedText.length < menuContent.length)
        }

    @Test
    fun indexDocument_marksFailedThenRecoversWhenUriBecomesReadable() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document
            val sequenceExtractor = SequenceDocumentTextExtractor(
                mutableListOf(
                    DocumentExtractionResult.Unreadable("open_failed"),
                    DocumentExtractionResult.Success(menuContent),
                ),
            )
            val retryPipeline = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
                documentTextExtractor = sequenceExtractor,
            )

            retryPipeline.indexDocument(document, forceExtract = true)
            val failed = documentRepository.records[document.id]!!
            assertEquals(ContentExtractStatus.FAILED, failed.contentExtractStatus)
            assertTrue(failed.contentText.isNullOrBlank())

            retryPipeline.indexDocument(failed, forceExtract = true)
            val recovered = documentRepository.records[document.id]!!
            assertEquals(ContentExtractStatus.SUCCESS, recovered.contentExtractStatus)
            assertEquals(menuContent.trim(), recovered.contentText?.trim())
            assertEquals(2, sequenceExtractor.callCount)
        }

    @Test
    fun indexDocument_withoutExtractor_keepsMetadataOnlyBehavior() =
        runTest {
            val bare = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
            )
            val document = menuDocument()
            documentRepository.records[document.id] = document

            bare.indexDocument(document)

            assertNull(documentRepository.records[document.id]!!.contentText)
            assertNotNull(documentRepository.records[document.id]!!.embeddingId)
        }

    @Test
    fun search_todaysMessMenu_returnsTodaysSectionAsSnippet() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document
            pipeline.indexDocument(document)

            val page = service.search(
                request = SearchRequest(query = "todays mess menu", limit = 5, indexOnQuery = false),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertTrue(page.count >= 1)
            val hit = page.items.first()
            assertEquals(document.id, hit.objectId)
            // snippet keeps the shareable URI for downstream chaining
            assertEquals("content://docs/menu", hit.snippet)

            val today = LocalDate.now().dayOfWeek
            val tomorrow = today.plus(1)
            val contentSnippet = hit.contentSnippet
            assertNotNull(contentSnippet)
            assertTrue(contentSnippet!!.contains("lunch-${today.name.lowercase()}"))
            assertFalse(contentSnippet.contains("lunch-${tomorrow.name.lowercase()}"))
        }

    @Test
    fun search_withoutDateReference_returnsLeadSnippet() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document
            pipeline.indexDocument(document)

            val page = service.search(
                request = SearchRequest(query = "mess menu", limit = 5, indexOnQuery = false),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertTrue(page.count >= 1)
            val contentSnippet = page.items.first().contentSnippet
            assertNotNull(contentSnippet)
            assertTrue(contentSnippet!!.contains("WEEKLY MESS MENU"))
        }

    @Test
    fun search_thisMonthsMessMenu_stripsMonthWordsAndReturnsSnippet() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document
            pipeline.indexDocument(document)

            val page = service.search(
                request = SearchRequest(
                    query = "this months mess menu",
                    limit = 5,
                    indexOnQuery = false,
                ),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertTrue(page.count >= 1)
            assertEquals(document.id, page.items.first().objectId)
            // Weekly menu has no month heading → lead snippet of the mess menu.
            val contentSnippet = page.items.first().contentSnippet
            assertNotNull(contentSnippet)
            assertTrue(contentSnippet!!.contains("WEEKLY MESS MENU"))
            // Embedding query should have been date-stripped to focus on "mess menu".
            assertTrue(
                embeddingGenerator.lastEmbeddedText.orEmpty().contains("mess menu") ||
                    embeddingGenerator.lastEmbeddedText.orEmpty().contains("mess-menu"),
            )
        }

    @Test
    fun indexDocuments_backfillsExtractableDocsMissingContentText() =
        runTest {
            // Simulate a pre-content-extraction index: embedding present, contentText null.
            val stale = menuDocument().copy(embeddingId = UUID.randomUUID())
            documentRepository.records[stale.id] = stale

            pipeline.indexDocuments(limit = 10)

            val stored = documentRepository.records[stale.id]!!
            assertEquals(menuContent.trim(), stored.contentText?.trim())
            assertNotNull(stored.summary)
            assertNotNull(stored.embeddingId)
            assertTrue(embeddingGenerator.lastEmbeddedText.orEmpty().contains("WEEKLY MESS MENU"))
        }

    @Test
    fun indexDocuments_backfillsMissingSummary() =
        runTest {
            val stale = menuDocument().copy(
                embeddingId = UUID.randomUUID(),
                contentText = menuContent,
                summary = null,
            )
            documentRepository.records[stale.id] = stale

            pipeline.indexDocuments(limit = 10)

            val stored = documentRepository.records[stale.id]!!
            assertNotNull(stored.summary)
            assertTrue(stored.summary!!.contains("mess-menu.pdf"))
            assertTrue(embeddingGenerator.lastEmbeddedText.orEmpty().contains("WEEKLY MESS MENU"))
        }

    @Test
    fun search_hybridRank_prefersDistinctiveFilenameOverNoisyLongContent() =
        runTest {
            val noisyOther = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/noise",
                name = "random-notes.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val prospectus = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/bookly",
                name = "BooklyProspectusReport.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val noiseBody = buildString {
                repeat(200) { append("generic campus handbook chapter about mess dining prospectus placements ") }
            }
            val booklyBody = "BOOKLY PROSPECTUS REPORT\nCompany overview and placement outcomes for Bookly."
            // Rebind pipeline extractor for this test's URIs.
            val localPipeline = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
                documentTextExtractor = FakeDocumentTextExtractor(
                    mapOf(
                        "content://docs/noise" to noiseBody,
                        "content://docs/bookly" to booklyBody,
                    ),
                ),
            )
            val localService = SemanticSearchService(
                embeddingGenerator = embeddingGenerator,
                imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                vectorIndex = vectorIndex,
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                documentDao = FakeDocumentDao(documentRepository),
                searchIndexPipeline = localPipeline,
                mediaStoreIngestionService = MediaStoreIngestionService(
                    mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
                    downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
                    documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
                    photoDao = FakePhotoDao(photoRepository),
                    documentDao = FakeDocumentDao(documentRepository),
                    photoRepository = photoRepository,
                    documentRepository = documentRepository,
                    embeddingIndexer = EmbeddingIndexer(
                        embeddingGenerator = embeddingGenerator,
                        imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                        embeddingRepository = FakeEmbeddingRepository(),
                        vectorIndex = vectorIndex,
                        ocrEngine = StubOcrEngine(),
                    ),
                    searchIndexPipeline = localPipeline,
                    photoImageLoader = PhotoImageLoader { null },
                    logger = NoOpRuntimeLogger(),
                ),
                fullDeviceIndexer = null,
                logger = NoOpRuntimeLogger(),
            )

            documentRepository.records[noisyOther.id] = noisyOther
            documentRepository.records[prospectus.id] = prospectus
            localPipeline.indexDocument(noisyOther)
            localPipeline.indexDocument(prospectus)

            val page = localService.search(
                request = SearchRequest(
                    query = "bookly prospectus report",
                    limit = 5,
                    indexOnQuery = false,
                ),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertTrue(page.count >= 1)
            assertEquals(prospectus.id, page.items.first().objectId)
            assertEquals("BooklyProspectusReport.pdf", page.items.first().title)
            // Stage B still exposes full-content snippet, not only the short summary.
            assertNotNull(page.items.first().contentSnippet)
            assertTrue(page.items.first().contentSnippet!!.contains("placement", ignoreCase = true))
        }

    @Test
    fun search_qa_usesFullContentTextNotSummary() =
        runTest {
            val document = menuDocument()
            documentRepository.records[document.id] = document
            pipeline.indexDocument(document)
            val stored = documentRepository.records[document.id]!!
            assertNotNull(stored.summary)
            assertTrue(stored.summary!!.length < stored.contentText!!.length)

            val page = service.search(
                request = SearchRequest(query = "todays mess menu", limit = 5, indexOnQuery = false),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertEquals(document.id, page.items.first().objectId)
            val today = LocalDate.now().dayOfWeek
            val snippet = page.items.first().contentSnippet
            assertNotNull(snippet)
            // Stage B answer excerpt comes from full contentText, not the short summary.
            assertTrue(snippet!!.contains("lunch-${today.name.lowercase()}"))
            assertFalse(stored.summary!!.contains("lunch-${today.name.lowercase()}"))
        }

    @Test
    fun search_latestTimetable_prefersNewestOverOldNamedFile() =
        runTest {
            val now = System.currentTimeMillis()
            val oldDays = 120L * 24 * 60 * 60 * 1000
            val oldTimetable = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/old-timetable",
                name = "timetable.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = now - oldDays,
                modifiedAt = now - oldDays,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val newTimetable = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/new-timetable",
                name = "class-schedule-aug.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = now,
                modifiedAt = now,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val todayName = LocalDate.now().dayOfWeek.name
            val oldBody = """
                WEEKLY TIMETABLE
                $todayName
                08:00-09:00 OLD Physics
            """.trimIndent()
            val newBody = """
                WEEKLY TIMETABLE
                $todayName
                12:00-13:00 NEW DSP
                13:00-14:00 NEW Data Structures
            """.trimIndent()
            val localPipeline = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
                documentTextExtractor = FakeDocumentTextExtractor(
                    mapOf(
                        "content://docs/old-timetable" to oldBody,
                        "content://docs/new-timetable" to newBody,
                    ),
                ),
            )
            val localService = SemanticSearchService(
                embeddingGenerator = embeddingGenerator,
                imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                vectorIndex = vectorIndex,
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                documentDao = FakeDocumentDao(documentRepository),
                searchIndexPipeline = localPipeline,
                mediaStoreIngestionService = MediaStoreIngestionService(
                    mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
                    downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
                    documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
                    photoDao = FakePhotoDao(photoRepository),
                    documentDao = FakeDocumentDao(documentRepository),
                    photoRepository = photoRepository,
                    documentRepository = documentRepository,
                    embeddingIndexer = EmbeddingIndexer(
                        embeddingGenerator = embeddingGenerator,
                        imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                        embeddingRepository = FakeEmbeddingRepository(),
                        vectorIndex = vectorIndex,
                        ocrEngine = StubOcrEngine(),
                    ),
                    searchIndexPipeline = localPipeline,
                    photoImageLoader = PhotoImageLoader { null },
                    logger = NoOpRuntimeLogger(),
                ),
                fullDeviceIndexer = null,
                logger = NoOpRuntimeLogger(),
            )

            documentRepository.records[oldTimetable.id] = oldTimetable
            documentRepository.records[newTimetable.id] = newTimetable
            localPipeline.indexDocument(oldTimetable)
            localPipeline.indexDocument(newTimetable)

            for (query in listOf("latest timetable", "timetable", "current schedule")) {
                val page = localService.search(
                    request = SearchRequest(query = query, limit = 5, indexOnQuery = false),
                    traceId = UUID.randomUUID(),
                    objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
                )
                assertTrue("expected hits for $query", page.count >= 1)
                assertEquals(
                    "expected newest timetable for query=$query",
                    newTimetable.id,
                    page.items.first().objectId,
                )
                assertEquals("class-schedule-aug.pdf", page.items.first().title)
                assertNotNull(page.items.first().modifiedAt)
            }
        }

    @Test
    fun search_timetable_prefersRichSemesterGridOverToyStub() =
        runTest {
            val now = System.currentTimeMillis()
            val toy = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/weekly_timetable.txt",
                name = "weekly_timetable.txt",
                extension = "txt",
                mimeType = "text/plain",
                size = 100L,
                checksum = "",
                createdAt = now,
                modifiedAt = now, // newer — would win on pure recency
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val rich = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/timetable_iitd.html",
                name = "timetable_iitd.html",
                extension = "html",
                mimeType = "text/html",
                size = 8000L,
                checksum = "",
                createdAt = now - 10_000,
                modifiedAt = now - 10_000,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val todayName = LocalDate.now().dayOfWeek.name
            val toyBody = """
                WEEKLY TIMETABLE
                $todayName
                09:00-10:00 Physics
                12:00-13:00 DSP
            """.trimIndent()
            val richBody = """
                Semester 3 Timetable
                Time | Monday | Tuesday | Wednesday | Thursday | Friday
                8:00 AM - 9:00 AM | Free | Lec-AAA1111 | Lec-AAA1111 | Free | Lec-AAA1111
                9:30 AM - 11:00 AM | Lec-BBB2222 | Lec-CCC3333 | Lec-CCC3333 | Lec-BBB2222 | Lec-CCC3333
                5:00 PM - 6:30 PM | Lec-DDD4444 | Free | Free | Lec-DDD4444 | Free
            """.trimIndent()
            val localPipeline = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
                documentTextExtractor = FakeDocumentTextExtractor(
                    mapOf(
                        "content://docs/weekly_timetable.txt" to toyBody,
                        "content://docs/timetable_iitd.html" to richBody,
                    ),
                ),
            )
            val localService = SemanticSearchService(
                embeddingGenerator = embeddingGenerator,
                imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                vectorIndex = vectorIndex,
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                documentDao = FakeDocumentDao(documentRepository),
                searchIndexPipeline = localPipeline,
                mediaStoreIngestionService = MediaStoreIngestionService(
                    mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
                    downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
                    documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
                    photoDao = FakePhotoDao(photoRepository),
                    documentDao = FakeDocumentDao(documentRepository),
                    photoRepository = photoRepository,
                    documentRepository = documentRepository,
                    embeddingIndexer = EmbeddingIndexer(
                        embeddingGenerator = embeddingGenerator,
                        imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                        embeddingRepository = FakeEmbeddingRepository(),
                        vectorIndex = vectorIndex,
                        ocrEngine = StubOcrEngine(),
                    ),
                    searchIndexPipeline = localPipeline,
                    photoImageLoader = PhotoImageLoader { null },
                    logger = NoOpRuntimeLogger(),
                ),
                fullDeviceIndexer = null,
                logger = NoOpRuntimeLogger(),
            )

            documentRepository.records[toy.id] = toy
            documentRepository.records[rich.id] = rich
            localPipeline.indexDocument(toy)
            localPipeline.indexDocument(rich)

            val page = localService.search(
                request = SearchRequest(query = "timetable", limit = 5, indexOnQuery = false),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )
            assertTrue(page.count >= 1)
            assertEquals(rich.id, page.items.first().objectId)
            assertEquals("timetable_iitd.html", page.items.first().title)
            val snippet = page.items.first().contentSnippet.orEmpty()
            // Must not answer from the toy Physics/DSP stub.
            assertFalse(snippet.contains("Physics"))
            assertFalse(snippet.contains("DSP"))
            assertTrue(
                snippet.contains("AAA1111") ||
                    snippet.contains("BBB2222") ||
                    snippet.contains("CCC3333") ||
                    snippet.contains("DDD4444") ||
                    snippet.contains("Semester 3"),
            )
        }

    @Test
    fun search_contentKeywordsBeatMisleadingFilename() =
        runTest {
            val misleadingName = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/bookly-name-only",
                name = "BooklyProspectusReport.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val contentWinner = DocumentEntity(
                id = UUID.randomUUID(),
                path = "content://docs/scan-17",
                name = "scan-042.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis() - 86_400_000,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
            val localPipeline = SearchIndexPipeline(
                embeddingIndexer = EmbeddingIndexer(
                    embeddingGenerator = embeddingGenerator,
                    imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                    embeddingRepository = FakeEmbeddingRepository(),
                    vectorIndex = vectorIndex,
                    ocrEngine = StubOcrEngine(),
                ),
                photoDao = FakePhotoDao(photoRepository),
                documentDao = FakeDocumentDao(documentRepository),
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                photoImageLoader = PhotoImageLoader { null },
                documentTextExtractor = FakeDocumentTextExtractor(
                    mapOf(
                        "content://docs/bookly-name-only" to "Generic campus notes without placement data.",
                        "content://docs/scan-17" to
                            "BOOKLY PROSPECTUS REPORT detailed placement outcomes for Bookly interns.",
                    ),
                ),
            )
            val localService = SemanticSearchService(
                embeddingGenerator = embeddingGenerator,
                imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                vectorIndex = vectorIndex,
                photoRepository = photoRepository,
                documentRepository = documentRepository,
                documentDao = FakeDocumentDao(documentRepository),
                searchIndexPipeline = localPipeline,
                mediaStoreIngestionService = MediaStoreIngestionService(
                    mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
                    downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
                    documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
                    photoDao = FakePhotoDao(photoRepository),
                    documentDao = FakeDocumentDao(documentRepository),
                    photoRepository = photoRepository,
                    documentRepository = documentRepository,
                    embeddingIndexer = EmbeddingIndexer(
                        embeddingGenerator = embeddingGenerator,
                        imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32),
                        embeddingRepository = FakeEmbeddingRepository(),
                        vectorIndex = vectorIndex,
                        ocrEngine = StubOcrEngine(),
                    ),
                    searchIndexPipeline = localPipeline,
                    photoImageLoader = PhotoImageLoader { null },
                    logger = NoOpRuntimeLogger(),
                ),
                fullDeviceIndexer = null,
                logger = NoOpRuntimeLogger(),
            )

            documentRepository.records[misleadingName.id] = misleadingName
            documentRepository.records[contentWinner.id] = contentWinner
            localPipeline.indexDocument(misleadingName)
            localPipeline.indexDocument(contentWinner)

            val page = localService.search(
                request = SearchRequest(
                    query = "bookly prospectus placement",
                    limit = 5,
                    indexOnQuery = false,
                ),
                traceId = UUID.randomUUID(),
                objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
            )

            assertTrue(page.count >= 1)
            assertEquals(contentWinner.id, page.items.first().objectId)
            assertEquals("scan-042.pdf", page.items.first().title)
        }

    private fun menuDocument(): DocumentEntity =
        DocumentEntity(
            id = UUID.randomUUID(),
            path = "content://docs/menu",
            name = "mess-menu.pdf",
            extension = "pdf",
            mimeType = "application/pdf",
            size = 100L,
            checksum = "",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            indexedAt = null,
            projectId = null,
            embeddingId = null,
            importance = 0,
        )

    private class FakeDocumentTextExtractor(
        private val contentByUri: Map<String, String>,
    ) : DocumentTextExtractor {
        override suspend fun extract(
            uri: String,
            mimeType: String,
            extension: String,
            maxPages: Int,
            maxChars: Int,
        ): DocumentExtractionResult {
            val text = contentByUri[uri]?.take(maxChars)?.trim().orEmpty()
            return if (text.isBlank()) {
                DocumentExtractionResult.Empty
            } else {
                DocumentExtractionResult.Success(text)
            }
        }
    }

    private class SequenceDocumentTextExtractor(
        private val results: MutableList<DocumentExtractionResult>,
    ) : DocumentTextExtractor {
        var callCount: Int = 0
            private set

        override suspend fun extract(
            uri: String,
            mimeType: String,
            extension: String,
            maxPages: Int,
            maxChars: Int,
        ): DocumentExtractionResult {
            callCount++
            return if (results.isEmpty()) {
                DocumentExtractionResult.Empty
            } else {
                results.removeAt(0)
            }
        }
    }

    private class RecordingEmbeddingGenerator(
        private val delegate: EmbeddingGenerator,
    ) : EmbeddingGenerator {
        var lastEmbeddedText: String? = null
        override val dimension: Int get() = delegate.dimension
        override val modelVersion: String get() = delegate.modelVersion
        override val isLoaded: Boolean get() = delegate.isLoaded

        override suspend fun embed(text: String): EmbeddingResult {
            lastEmbeddedText = text
            return delegate.embed(text)
        }
    }

    private class StubOcrEngine : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult =
            OcrResult.Failure("unused")
    }

    private class FakePhotoRepository : PhotoRepository {
        val records = mutableMapOf<UUID, PhotoEntity>()
        override suspend fun insert(photo: PhotoEntity) { records[photo.id] = photo }
        override suspend fun update(photo: PhotoEntity) { records[photo.id] = photo }
        override suspend fun delete(id: UUID) { records.remove(id) }
        override suspend fun getById(id: UUID): PhotoEntity? = records[id]
        override fun observeById(id: UUID): Flow<PhotoEntity?> = emptyFlow()
        override suspend fun search(query: String): List<PhotoEntity> = emptyList()
    }

    private class FakeDocumentRepository : DocumentRepository {
        val records = mutableMapOf<UUID, DocumentEntity>()
        override suspend fun insert(document: DocumentEntity) { records[document.id] = document }
        override suspend fun update(document: DocumentEntity) { records[document.id] = document }
        override suspend fun delete(id: UUID) { records.remove(id) }
        override suspend fun getById(id: UUID): DocumentEntity? = records[id]
        override fun observeById(id: UUID): Flow<DocumentEntity?> = emptyFlow()
        override suspend fun search(query: String): List<DocumentEntity> = emptyList()
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        override suspend fun insert(embedding: EmbeddingEntity) = Unit
        override suspend fun update(embedding: EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID) = null
        override fun observeById(embeddingId: UUID): Flow<EmbeddingEntity?> = emptyFlow()
    }

    private class FakePhotoDao(
        private val repository: FakePhotoRepository,
    ) : PhotoDao {
        override suspend fun insert(photo: PhotoEntity) = repository.insert(photo)
        override suspend fun update(photo: PhotoEntity) = repository.update(photo)
        override suspend fun delete(photo: PhotoEntity) = repository.delete(photo.id)
        override suspend fun getById(id: UUID) = repository.getById(id)
        override fun observeById(id: UUID) = repository.observeById(id)
        override suspend fun getByUri(uri: String) = repository.records.values.firstOrNull { it.uri == uri }
        override suspend fun searchByOcr(query: String) = emptyList<PhotoEntity>()
        override suspend fun searchByOcrPaged(query: String, limit: Int, offset: Int) = emptyList<PhotoEntity>()
        override suspend fun countByOcr(query: String) = 0
        override suspend fun listRecent(limit: Int) = repository.records.values.take(limit)
        override suspend fun listUnindexedWithOcr(limit: Int) =
            repository.records.values.filter { it.embeddingId == null && !it.ocrText.isNullOrBlank() }.take(limit)
        override suspend fun listUnindexed(limit: Int) =
            repository.records.values.filter { it.embeddingId == null }.take(limit)
    }

    private class FakeDocumentDao(
        private val repository: FakeDocumentRepository,
    ) : DocumentDao {
        override suspend fun insert(document: DocumentEntity) = repository.insert(document)
        override suspend fun update(document: DocumentEntity) = repository.update(document)
        override suspend fun delete(document: DocumentEntity) = repository.delete(document.id)
        override suspend fun getById(id: UUID) = repository.getById(id)
        override fun observeById(id: UUID) = repository.observeById(id)
        override suspend fun getByPath(path: String) = repository.records.values.firstOrNull { it.path == path }
        override suspend fun searchByName(query: String) = emptyList<DocumentEntity>()
        override suspend fun searchFullText(query: String, limit: Int, offset: Int) =
            repository.records.values
                .filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.path.contains(query, ignoreCase = true) ||
                        it.extension.contains(query, ignoreCase = true) ||
                        it.summary.orEmpty().contains(query, ignoreCase = true) ||
                        it.contentText.orEmpty().contains(query, ignoreCase = true)
                }
                .sortedByDescending { it.modifiedAt }
                .drop(offset)
                .take(limit)
        override suspend fun countFullText(query: String) =
            repository.records.values.count {
                it.name.contains(query, ignoreCase = true) ||
                    it.path.contains(query, ignoreCase = true) ||
                    it.extension.contains(query, ignoreCase = true) ||
                    it.summary.orEmpty().contains(query, ignoreCase = true) ||
                    it.contentText.orEmpty().contains(query, ignoreCase = true)
            }
        override suspend fun getByProjectId(projectId: UUID) = emptyList<DocumentEntity>()
        override suspend fun listUnindexed(limit: Int) =
            repository.records.values.filter { it.embeddingId == null }.take(limit)
        override suspend fun listMissingContentText(limit: Int) =
            repository.records.values.filter {
                ContentExtractStatus.needsBackgroundExtraction(it.contentExtractStatus) &&
                    it.contentText.isNullOrBlank()
            }.take(limit)
        override suspend fun listMissingSummary(limit: Int) =
            repository.records.values.filter { it.summary == null }.take(limit)
        override suspend fun countAll() = repository.records.size
        override suspend fun countWithSummary() =
            repository.records.values.count { !it.summary.isNullOrBlank() }
        override suspend fun countMissingSummary() =
            repository.records.values.count { it.summary.isNullOrBlank() }
    }
}
