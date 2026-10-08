package me.bmax.apatch.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.bmax.apatch.ui.wallpaper.WallpaperCatalog
import me.bmax.apatch.ui.wallpaper.WallpaperDevice
import me.bmax.apatch.ui.wallpaper.WallpaperItem
import me.bmax.apatch.ui.wallpaper.WallpaperProvider
import me.bmax.apatch.ui.wallpaper.WallpaperProviderRegistry

data class WallpaperUiState(
    val providers: List<WallpaperProvider> = emptyList(),
    val selectedProviderId: String? = null,
    val device: WallpaperDevice = WallpaperDevice.PHONE,
    val items: List<WallpaperItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class WallpaperGalleryViewModel(private val app: Application) : AndroidViewModel(app) {

    private val catalog = WallpaperCatalog()
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0

    private val _state = MutableStateFlow(WallpaperUiState())
    val state: StateFlow<WallpaperUiState> = _state.asStateFlow()

    init {
        val providers = runCatching { WallpaperProviderRegistry.load(app) }.getOrDefault(emptyList())
        _state.update { it.copy(providers = providers, selectedProviderId = providers.firstOrNull()?.id) }
        loadGallery(clearCache = false)
    }

    private fun currentProvider(): WallpaperProvider? {
        val state = _state.value
        return state.providers.firstOrNull { it.id == state.selectedProviderId }
            ?: state.providers.firstOrNull()
    }

    fun selectDevice(device: WallpaperDevice) {
        if (_state.value.device == device) return
        _state.update { it.copy(device = device) }
        loadGallery(clearCache = false)
    }

    /** Retain measured proportions across scrolling, layout changes and activity rotation. */
    fun recordImageSize(item: WallpaperItem, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _state.update { state ->
            val existing = state.items.firstOrNull { it.url == item.url }
            if (existing == null || existing.width != null) state else state.copy(
                items = state.items.map {
                    if (it.url == item.url) it.copy(width = width, height = height) else it
                },
            )
        }
    }

    fun refresh() = loadGallery(clearCache = true)

    fun retry() = loadGallery(clearCache = false)

    private fun loadGallery(clearCache: Boolean) {
        val provider = currentProvider() ?: return
        val device = _state.value.device
        val refreshing = clearCache && _state.value.items.isNotEmpty()
        val requestGeneration = ++generation
        loadJob?.cancel()
        moreJob?.cancel()
        _state.update {
            it.copy(
                loading = !refreshing,
                refreshing = refreshing,
                loadingMore = false,
                error = null,
                items = emptyList(),
            )
        }
        loadJob = viewModelScope.launch {
            runCatching {
                if (clearCache) catalog.clearCache()
                catalog.load(provider, device, PAGE_SIZE, emptySet())
            }
                .onSuccess { list ->
                    if (requestGeneration != generation) return@onSuccess
                    _state.update { it.copy(loading = false, refreshing = false, items = list) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (requestGeneration != generation) return@onFailure
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = throwable.message ?: "failed")
                    }
                }
        }
    }

    fun loadMore() {
        val state = _state.value
        if (state.loading || state.refreshing || state.loadingMore) return
        val provider = currentProvider() ?: return
        val device = state.device
        val requestGeneration = generation
        val seen = state.items.mapTo(HashSet()) { it.url }
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            runCatching { catalog.load(provider, device, PAGE_SIZE, seen) }
                .onSuccess { list ->
                    if (requestGeneration != generation) return@onSuccess
                    _state.update { it.copy(loadingMore = false, items = it.items + list) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (requestGeneration != generation) return@onFailure
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 18

        fun Factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WallpaperGalleryViewModel(app) as T
        }
    }
}
