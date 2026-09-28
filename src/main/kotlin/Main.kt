import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Identity {
    const val NAME = "Rakha Daffa Tama Truski"
    const val NIM = "124140196"
}

private const val NEWS_INTERVAL_MS = 2000L
private const val TARGET_CATEGORY = "Tech"
private const val ERROR_TRIGGER_ID = 4

data class NewsItem(
    val id: Int,
    val title: String,
    val category: String,
    val content: String
)

private val dummyNews: List<NewsItem> = listOf(
    NewsItem(1, "kotlin 2.1 rilis dengan kompilasi lebih cepat", "Tech", "Bahasa Kotlin resmi rilis dengan waktu kompilasi lebih singkat."),
    NewsItem(2, "sensor sidik jari under display generasi baru", "Tech", "Smartphone memakai sensor sidik jari optik terbaru."),
    NewsItem(3, "harga emas dunia naik tiga persen", "Finance", "Harga emas global naik tipis pada penutupan sesi."),
    NewsItem(4, "framework web baru dengan hot reload instan", "Tech", "Framework web generasi baru menawarkan hot reload instan."),
    NewsItem(5, "timnas indonesia menang lawan australia", "Sports", "Timnas Indonesia menang dengan skor 2-1.")
)

val readNewsCount = MutableStateFlow(0)

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun log(message: String) {
    println("[" + LocalTime.now().format(timeFormatter) + "] " + message)
}

fun fetchNewsFeed(simulateError: Boolean = false): Flow<NewsItem> = flow {
    log("Flow builder aktif, mengirim " + dummyNews.size + " berita (tiap " + NEWS_INTERVAL_MS + "ms)")
    for (news in dummyNews) {
        delay(NEWS_INTERVAL_MS)
        if (simulateError && news.id == ERROR_TRIGGER_ID) {
            throw IOException("Server berita down (503) saat mengambil berita #" + news.id)
        }
        log("emit -> #" + news.id + " " + news.title)
        emit(news)
    }
}
    .filter { news -> news.category == TARGET_CATEGORY }
    .map { news -> news.copy(title = "[" + TARGET_CATEGORY + "] " + news.title.uppercase()) }

suspend fun fetchNewsDetailAsync(news: NewsItem): String = coroutineScope {
    val metaTask = async(Dispatchers.IO) {
        delay(400)
        "kategori=" + news.category + ", id=" + news.id
    }
    val bodyTask = async(Dispatchers.IO) {
        delay(800)
        news.content
    }
    val meta = metaTask.await()
    val body = bodyTask.await()
    meta + " | " + body
}

suspend fun runSimulation() {
    val detailCache = mutableListOf<String>()

    log("Mulai collect news feed, hanya kategori " + TARGET_CATEGORY)

    fetchNewsFeed()
        .onEach { news -> log("collect menerima berita #" + news.id + ": " + news.title) }
        .collect { news ->
            val detail = fetchNewsDetailAsync(news)
            detailCache.add(detail)
            readNewsCount.value += 1
            log("detail #" + news.id + " -> " + detail)
        }

    log("Selesai collect, jumlah berita terbaca = " + detailCache.size)

    log("Skenario error: menjalankan flow dengan simulateError = true")
    var errorHandled = false
    fetchNewsFeed(simulateError = true)
        .catch { throwable ->
            errorHandled = true
            log(".catch menangkap: " + throwable.message)
        }
        .collect { news -> log("  skenario error: menerima #" + news.id) }

    log("Error tertangani oleh .catch = " + errorHandled)
}

private fun printHeader() {
    val line = "=".repeat(62)
    println(line)
    println(" NEWS FEED SIMULATOR - PRAKTIKUM KOTLIN COROUTINES & FLOW")
    println(line)
    println(" Nama : " + Identity.NAME)
    println(" NIM  : " + Identity.NIM)
    println(line)
}

private fun printSummary() {
    val line = "=".repeat(62)
    println()
    println(line)
    println(" STATUS AKHIR SIMULASI")
    println(line)
    println(" Nama              : " + Identity.NAME)
    println(" NIM               : " + Identity.NIM)
    println(" Kategori difilter : " + TARGET_CATEGORY)
    println(" Interval emit     : " + NEWS_INTERVAL_MS + " ms")
    println(" Total berita (StateFlow readNewsCount) : " + readNewsCount.value)
    println(" Status            : SIMULASI SELESAI NORMAL")
    println(line)
}

fun main() = runBlocking {
    printHeader()

    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log("CoroutineExceptionHandler: " + throwable::class.java.simpleName + " - " + throwable.message)
    }

    val counterJob = launch(Dispatchers.Default) {
        readNewsCount.collect { total -> log("StateFlow -> jumlah berita terbaca: " + total) }
    }

    val simulationJob = launch(Dispatchers.Default + exceptionHandler) {
        runSimulation()
    }

    simulationJob.join()
    counterJob.cancel()

    printSummary()
}




