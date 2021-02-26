package com.nek.photogallery.api

import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type


private const val TAG = "PhotoDeserializerTag"

class PhotoDeserializer : JsonDeserializer<PhotoResponse> {
    override fun deserialize(
        json: JsonElement?, typeOfT: Type?,
        context: JsonDeserializationContext?
    ): PhotoResponse {
        val pageobject = json?.asJsonObject?.get("photos")?.asJsonObject
        val pages = pageobject?.get("pages")?.asNumber?.toInt()
        Log.d(TAG, "pages: $pages")
        val jsonlist = pageobject?.get("photo")?.asJsonArray

        val list: List<GalleryItem>? = jsonlist?.map {
            val jsonItem = it.asJsonObject
            //Log.d(TAG,"Got item: $jsonItem")
            val url = if (jsonItem.has("url_s")) {
                jsonItem.get("url_s").asString
            } else {
                ""
            }

            val item = GalleryItem(
                jsonItem.get("title").asString,
                jsonItem.get("id").asString,
                url
            )
            //Log.d(TAG, item.toString())
            item
        }

        return PhotoResponse().apply {
            galleryItems = list ?: emptyList()
            pageCount = pages
        }
    }

}
