package com.nek.photogallery.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface FlickrApi {
    @GET("services/rest?method=flickr.interestingness.getList")
    suspend fun fetchPhotos(@Query("page") page: Int): Response<PhotoResponse>

    @GET
    fun fetchUrlBytes(@Url url: String): Call<ResponseBody>

    @GET("services/rest?method=flickr.photos.search")
    suspend fun searchPhotos(@Query("text") query: String, @Query("page") page: Int): Response<PhotoResponse>

}
