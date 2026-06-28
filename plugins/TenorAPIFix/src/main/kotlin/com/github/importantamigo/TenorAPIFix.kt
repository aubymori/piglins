package com.github.importantamigo

import android.content.Context
import com.aliucord.Http
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.InsteadHook
import com.aliucord.utils.GsonUtils
import com.aliucord.utils.GsonUtils.fromJson
import com.discord.models.gifpicker.dto.ModelGif
import com.discord.stores.StoreGifPicker
import com.discord.stores.StoreStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Collections

@AliucordPlugin
class TenorAPIFix : Plugin() {

    init {

        settingsTab = SettingsTab(PluginSettings::class.java, SettingsTab.Type.BOTTOM_SHEET).withArgs(settings)
    }

    companion object {
        const val DEFAULT_TENOR_KEY = "3Z0688EVWYKH"
        private const val GIF_LIMIT = 50
        private const val LOCALE = "en-US"
    }

    private data class TenorMedia(
        val url: String?,
        val preview: String?,
        val dims: List<Int>?
    )

    private data class TenorResult(
        val id: String,
        val itemurl: String?,
        val media: List<Map<String, TenorMedia>>?
    )

    private data class TenorResponse(val results: List<TenorResult>)


    override fun start(context: Context) {
        val fetchTrending = StoreGifPicker::class.java.getDeclaredMethod("fetchTrendingCategoryGifs")
        val fetchSearch = StoreGifPicker::class.java.getDeclaredMethod("fetchGifsForSearchQuery", String::class.java)

        patcher.patch(fetchTrending, InsteadHook { callFrame ->
            val store = callFrame.thisObject as StoreGifPicker
            fetchGifsAsync(store, null)
            null
        })

        patcher.patch(fetchSearch, InsteadHook { callFrame ->
            val store = callFrame.thisObject as StoreGifPicker
            val query = callFrame.args[0] as String
            fetchGifsAsync(store, query)
            null
        })
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }


    private fun fetchGifsAsync(store: StoreGifPicker, query: String?) {
        Thread({
            try {
                val gifs = fetchGifs(query)
                runOnStoreThread {
                    handleFetchSuccess(store, query, gifs)
                }
            } catch (t: Throwable) {
                logger.error("Failed to fetch GIFs from Tenor", t)
                runOnStoreThread {
                    handleFetchError(store, query)
                }
            }
        }, "TenorAPIFix").start()
    }

    // --- Network ---

    private fun fetchGifs(query: String?): List<ModelGif> {
        val TENOR_KEY = settings.getString("apiKey", DEFAULT_TENOR_KEY)
        val url = if (query == null) {
            "https://api.tenor.com/v1/trending?key=$TENOR_KEY&locale=$LOCALE&limit=$GIF_LIMIT"
        } else {
            "https://api.tenor.com/v1/search?key=$TENOR_KEY&q=${encode(query)}&locale=$LOCALE&limit=$GIF_LIMIT"
        }

        val response = Http.Request(url, "GET").execute()
        val body = response.text()
        logger.debug("Tenor response: $body")

        if (response.statusCode !in 200..299) {
            throw IOException("Tenor request failed: ${response.statusCode} $body")
        }

        return parseGifResponse(body)
    }

    private fun parseGifResponse(body: String): List<ModelGif> {
        val root = GsonUtils.gson.fromJson(body, TenorResponse::class.java)

        return root.results.mapNotNull { item ->
            val media = item.media?.firstOrNull() ?: return@mapNotNull null

            val tinygif = media["tinygif"]
            val gif = media["gif"]

            val gifImageUrl = tinygif?.url ?: gif?.url ?: return@mapNotNull null
            val tenorGifUrl = item.itemurl ?: gif?.url ?: gifImageUrl
            val width = gif?.dims?.getOrNull(0) ?: tinygif?.dims?.getOrNull(0) ?: 0
            val height = gif?.dims?.getOrNull(1) ?: tinygif?.dims?.getOrNull(1) ?: 0

            ModelGif(gifImageUrl, tenorGifUrl, width, height)
        }
    }

    private fun handleFetchSuccess(store: StoreGifPicker, query: String?, gifs: List<ModelGif>) {
        try {
            if (query == null) {
                StoreGifPicker.`access$handleFetchTrendingGifsOnNext`(store, gifs)
            } else {
                StoreGifPicker.`access$handleGifSearchResults`(store, query, gifs)
            }
        } catch (t: Throwable) {
            logger.error("Failed to update GIF picker state", t)
        }
    }

    private fun handleFetchError(store: StoreGifPicker, query: String?) {
        try {
            if (query == null) {
                StoreGifPicker.`access$handleFetchTrendingGifsError`(store)
            } else {
                StoreGifPicker.`access$handleGifSearchResults`(store, query, Collections.emptyList<ModelGif>())
            }
        } catch (t: Throwable) {
            logger.error("Failed to update GIF picker error state", t)
        }
    }


    private fun runOnStoreThread(block: () -> Unit) {
        StoreStream.`access$getDispatcher$p`(StoreStream.getNotices().stream).schedule {
            block()
        }
    }

    private fun encode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (t: Throwable) {
            value
        }
    }
}
