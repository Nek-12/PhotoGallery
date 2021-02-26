package com.nek.photogallery.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


private const val TAG = "FlickrFetchr"

class FlickrFetchr {
    private val flickrApi: FlickrApi
    var pageCount: Int? = null
    private set

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(PhotoInterceptor())
            .build()

        val gsonBuilder = GsonBuilder().registerTypeAdapter(PhotoResponse::class.java, PhotoDeserializer())
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://api.flickr.com/")
            .addConverterFactory(GsonConverterFactory.create(gsonBuilder.create()))
            .client(client)
            .build()
        flickrApi = retrofit.create(FlickrApi::class.java)
    }

    suspend fun getPhotos(page: Int, query: String = ""): List<GalleryItem> {
        return if (query.isEmpty()) fetchPhotos(page) else searchPhotos(query,page)
    }

    private suspend fun fetchPhotos(page: Int): List<GalleryItem> {
        return fetchPhotoMetadata(flickrApi.fetchPhotos(page),page)
    }

    private suspend fun searchPhotos(query: String, page: Int):List<GalleryItem> {
        return fetchPhotoMetadata(flickrApi.searchPhotos(query,page),page)
    }


    private fun fetchPhotoMetadata(response: Response<PhotoResponse>, page: Int): List<GalleryItem> {
        var galleryItems: List<GalleryItem> = response.body()?.galleryItems ?: emptyList()
        pageCount = response.body()?.pageCount
        galleryItems = galleryItems.filterNot {
            it.url.isBlank()
        }
        Log.i(TAG, "Got List for page ($page)")
        return galleryItems
    }

    @WorkerThread
    fun fetchPhoto(url: String): Bitmap? {
        val response: Response<ResponseBody> = flickrApi.fetchUrlBytes(url).execute()
        //Log.i(TAG, "Decoded bitmap=$bitmap from Response=$response")
        return response.body()?.byteStream()?.use(BitmapFactory::decodeStream)
    }

}
