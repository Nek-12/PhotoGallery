package com.nek.photogallery.api

import com.google.gson.annotations.SerializedName

// Contains a list of items, contained in FlickrResponse
class PhotoResponse {
    @SerializedName("photo")
    lateinit var galleryItems: List<GalleryItem>
    var pageCount: Int? = null
}
