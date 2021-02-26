package com.nek.photogallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.nek.photogallery.api.FlickrFetchr
import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0
private const val MESSAGE_PRELOAD = 1

class ThumbnailDownloader<in T>(
    private val ownerLifecycle: Lifecycle,
    private val responseHandler: Handler,
    private val onThumbnailDownloaded: (T, Bitmap) -> Unit
) : HandlerThread(TAG) {
    private val fetchr = FlickrFetchr()
    private var hasQuit = false
    private lateinit var requestHandler: Handler
    private val requestMap = ConcurrentHashMap<T, String>()
    private val cache: LruCache<String, Bitmap> = LruCache(100)
    val fragmentLifecycleObserver: LifecycleObserver =
        object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            fun setup() {
                Log.i(TAG, "Starting background thread")
                start()
                looper
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun tearDown() {
                Log.i(TAG, "Destroying background thread")
                ownerLifecycle.removeObserver(this)
                quit()
            }
        }

    init {
        ownerLifecycle.addObserver(fragmentLifecycleObserver)
    }



    fun clearQueue() {
        Log.i(TAG, "Clearing all requests from queue")
        requestHandler.removeMessages(MESSAGE_DOWNLOAD)
        requestMap.clear()
        cache.evictAll()
    }


    override fun quit(): Boolean {
        hasQuit = true
        return super.quit()
    }

    fun queueThumbnail(target: T, url: String) {
        //Log.i(TAG, "Got a URL: $url")
        requestMap[target] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget()
    }

    fun preloadThumbnal(url: String) {
        requestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget()
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("HandlerLeak")
    override fun onLooperPrepared() {
        requestHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    val target = msg.obj as T
                    //Log.i(TAG, "Got a request for URL: ${requestMap[target!!]}")
                    handleRequest(target)
                } else if (msg.what == MESSAGE_PRELOAD) {
                    val url = msg.obj as String
                    //Log.d(TAG, "Handling preload for $url")
                    handlePreload(url)
                }
            }
        }
    }

    private fun handlePreload(url: String) {
        var bitmap = cache.get(url)
        if (bitmap == null) {
            try {
                bitmap = fetchr.fetchPhoto(url) ?: return
            } catch (e: ConnectException) {
                Log.e(TAG, "Network failure", e)
                return
            }
            cache.put(url,bitmap)
        }
    }

    private fun handleRequest(target: T) {
        val url = requestMap[target ?: return] ?: return
        var bitmap = cache.get(url)
        if (bitmap == null) {
            bitmap = fetchr.fetchPhoto(url) ?: return
            cache.put(url,bitmap)
        }
        responseHandler.post(Runnable {
            if (requestMap[target] != url || hasQuit) {
                return@Runnable
            }
            requestMap.remove(target)
            onThumbnailDownloaded(target, bitmap)
        })

    }

}
