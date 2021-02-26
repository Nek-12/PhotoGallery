package com.nek.photogallery

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.nek.photogallery.api.GalleryItem
import com.nek.photogallery.databinding.PhotoGalleryFragmentBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "PhotoGalleryFragment"
private const val COL_WIDTH = 400
private const val PRELOAD_ITEMS = 10
private const val POLL_WORK = "POLL_WORK"

class PhotoGalleryFragment : VisibleFragment() {
    private var _b: PhotoGalleryFragmentBinding? = null
    private val b get() = _b!!

    private lateinit var photoGalleryViewModel: PhotoGalleryViewModel
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoHolder>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        photoGalleryViewModel =
            ViewModelProvider(this).get(PhotoGalleryViewModel::class.java)
        //for learning purposes as advised
        retainInstance = true
        val responseHandler = Handler(Looper.myLooper()!!)
        thumbnailDownloader =
            ThumbnailDownloader(lifecycle, responseHandler) { photoHolder, bitmap ->
                val drawable = BitmapDrawable(resources, bitmap)
                photoHolder.clickEnabled = true
                photoHolder.bindDrawable(drawable)
            }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_photo_gallery, menu)
        val searchItem: MenuItem = menu.findItem(R.id.menu_item_search)
        (searchItem.actionView as SearchView).apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(queryText: String): Boolean {
                    Log.d(TAG, "QueryTextSubmit: $queryText")
                    photoGalleryViewModel.query.value = queryText
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(queryText: String): Boolean {
                    Log.d(TAG, "QueryTextChange: $queryText")
                    return false
                }
            })
            setOnSearchClickListener {
                this.setQuery(photoGalleryViewModel.searchTerm, false)
            }
        }
        val toggleItem = menu.findItem(R.id.menu_item_toggle_polling)
        val isPolling = QueryPreferences.isPolling(requireContext())
        val toggleItemTitle = if (isPolling) {
            R.string.stop_polling
        } else {
            R.string.start_polling
        }
        toggleItem.setTitle(toggleItemTitle)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_clear -> {
                photoGalleryViewModel.query.value = ""
                thumbnailDownloader.clearQueue()
                true
            }
            R.id.menu_item_toggle_polling -> {
                val isPolling = QueryPreferences.isPolling(requireContext())
                if (isPolling) {
                    WorkManager.getInstance(requireContext()).cancelUniqueWork(POLL_WORK)
                    QueryPreferences.setPolling(requireContext(), false)
                } else {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                    val periodicRequest = PeriodicWorkRequest
                        .Builder(PollWorker::class.java, 15, TimeUnit.MINUTES)
                        //.setConstraints(constraints)
                        .build()
                    WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                        POLL_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        periodicRequest
                    )
                    QueryPreferences.setPolling(requireContext(), true)
                }
                activity?.invalidateOptionsMenu()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = PhotoGalleryFragmentBinding.inflate(inflater, container, false)
        b.photoRecyclerView.layoutManager = GridLayoutManager(context, 3)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = PhotoAdapter()
        b.photoRecyclerView.adapter = adapter
        b.retryBtn.isVisible = false
        lifecycleScope.launch {
            photoGalleryViewModel.galleryFlow.collectLatest {
                adapter.submitData(it)
            }
        }
        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                b.progressHorizontal.isVisible = loadStates.refresh is LoadState.Loading
                b.retryBtn.isVisible = loadStates.refresh is LoadState.Error
                b.errorText.isVisible = loadStates.refresh is LoadState.Error
            }

            view.viewTreeObserver.addOnGlobalLayoutListener {
                val columns = view.width / COL_WIDTH
                val manager = b.photoRecyclerView.layoutManager as GridLayoutManager
                manager.spanCount = columns
            }
        }
        b.retryBtn.setOnClickListener {
            photoGalleryViewModel.refresh()
            thumbnailDownloader.clearQueue()
        }

    }


    private inner class PhotoHolder(itemImageView: ImageView) : RecyclerView.ViewHolder(itemImageView),
        View.OnClickListener {
        private var galleryItem: GalleryItem? = null
        var clickEnabled = false

        init {
            itemView.setOnClickListener(this)
        }

        val bindDrawable: (Drawable) -> Unit = itemImageView::setImageDrawable

        fun bindGalleryItem(item: GalleryItem?) {
            galleryItem = item
        }

        override fun onClick(view: View) {
            if (!clickEnabled) return

            galleryItem?.photoPageUri?.let {
//                val intent = PhotoPageActivity.newIntent(requireContext(), it)
//                startActivity(intent)
                val color = ContextCompat.getColor(
                    requireContext(), R.color.colorPrimary)
                val params = CustomTabColorSchemeParams.Builder()
                    .setNavigationBarColor(color)
                    .setToolbarColor(color)
                    .build()
                CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams( params )
                    .setShowTitle(true)
                    .build()
                    .launchUrl(requireContext(), it)
            }
        }
    }

    private inner class PhotoAdapter :
        PagingDataAdapter<GalleryItem, PhotoHolder>(diffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val view = layoutInflater.inflate(
                R.layout.list_item_gallery,
                parent,
                false
            ) as ImageView
            return PhotoHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val placeholder: Drawable = ColorDrawable()
            holder.clickEnabled = false //don't allow to click blank images
            holder.bindDrawable(placeholder)
            holder.bindGalleryItem(getItem(position))
            //and preload some more
            for (i in position - PRELOAD_ITEMS..position + PRELOAD_ITEMS) {
                if (i < 0)
                    continue
                val item = getItem(i)
                if (item != null) {
                    thumbnailDownloader.preloadThumbnal(item.url)
                }
                //Log.i(TAG, "preloading from ${position - PRELOAD_ITEMS} to ${position+PRELOAD_ITEMS}")
            }
            //load the item last (so that it is processed first)
            getItem(position)?.url?.let {
                thumbnailDownloader.queueThumbnail(holder, it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
        thumbnailDownloader.clearQueue()
    }

    companion object {
        fun newInstance() = PhotoGalleryFragment()

        private val diffCallback = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean =
                oldItem == newItem
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(
            thumbnailDownloader.fragmentLifecycleObserver
        )
    }
}


