package com.nek.photogallery

import android.accounts.NetworkErrorException
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.paging.*
import com.nek.photogallery.api.FlickrFetchr
import com.nek.photogallery.api.FlickrPagingSource
import com.nek.photogallery.api.GalleryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch


private const val TAG = "PhotoGalleyViewModel"

class PhotoGalleryViewModel (private val app: Application) : AndroidViewModel(app) {
    var galleryFlow: Flow<PagingData<GalleryItem>>
    private val flickrFetchr = FlickrFetchr()
    private var dataSource: FlickrPagingSource? = null
    var query = MutableLiveData<String>()

    val searchTerm: String
        get() = query.value ?: ""


    init {
        galleryFlow = Pager(
            PagingConfig(
                pageSize = 100,
                enablePlaceholders = true,
                maxSize = 500,
            )
        ) {
            FlickrPagingSource(
                ::fetchPhotos,
                { flickrFetchr.pageCount }
            ).also { dataSource = it }
        }.flow.cachedIn(viewModelScope).catch { cause ->
            Log.e(TAG,"Exception in Pager",cause)
        }
        query.value = QueryPreferences.getStoredQuery(app)

        query.observeForever{
            refresh()
        }
    }

    private suspend fun fetchPhotos(page: Int): List<GalleryItem> {
        Log.d(TAG, "Searching for ${query.value}")
        return flickrFetchr.getPhotos(page, query.value!!)
    }

    fun refresh() {
        dataSource?.invalidate()
        QueryPreferences.setStoredQuery(app, query.value?: "")
    }



}
