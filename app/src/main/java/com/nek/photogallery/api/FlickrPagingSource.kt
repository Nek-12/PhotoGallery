package com.nek.photogallery.api

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.nek.photogallery.PhotoGalleryViewModel
import retrofit2.HttpException
import java.io.IOException

class FlickrPagingSource(
    private val dataLoadFunctor: suspend (Int) -> List<GalleryItem>,
    private val pageCountFunctor: () -> Int?
) : PagingSource<Int, GalleryItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryItem> {
        return try {
            val pageNumber = params.key ?: 0
            Log.d("FlickrPagingSource", "calling dataLoadFunctor")
            val response = dataLoadFunctor(pageNumber)
            val prevKey = if (pageNumber > 0) pageNumber - 1 else null
            var nextKey: Int? = null
            pageCountFunctor()?.let {
                if (pageNumber < it)
                    nextKey = pageNumber + 1
            }
            LoadResult.Page(
                data = response,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, GalleryItem>): Int? {
        return state.anchorPosition?.let {
            state.closestPageToPosition(it)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(it)?.nextKey?.minus(1)
        }
    }
}
