package com.webworkspace.app.ui

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.webworkspace.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaItem(val uri: Uri, val isVideo: Boolean)

class GalleryActivity : AppCompatActivity() {

    private lateinit var rvGallery: RecyclerView
    private lateinit var tvEmptyGallery: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val toolbar = findViewById<MaterialToolbar>(R.id.galleryToolbar)
        toolbar.title = "FlowMedia Gallery"
        toolbar.setNavigationOnClickListener { finish() }

        rvGallery = findViewById(R.id.rvGallery)
        tvEmptyGallery = findViewById(R.id.tvEmptyGallery)

        rvGallery.layoutManager = GridLayoutManager(this, 2)

        loadMedia()
    }

    private fun loadMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            // Load Videos
            val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            val videoSelection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("FlowMedia_%")

            contentResolver.query(videoCollection, videoProjection, videoSelection, selectionArgs, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItem(contentUri, true))
                }
            }

            // Load Images
            val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            
            contentResolver.query(imageCollection, imageProjection, videoSelection, selectionArgs, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItem(contentUri, false))
                }
            }

            withContext(Dispatchers.Main) {
                if (mediaList.isEmpty()) {
                    tvEmptyGallery.visibility = View.VISIBLE
                    rvGallery.visibility = View.GONE
                } else {
                    tvEmptyGallery.visibility = View.GONE
                    rvGallery.visibility = View.VISIBLE
                    rvGallery.adapter = GalleryAdapter(mediaList) { item ->
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
                        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(Intent.createChooser(intent, "Open with"))
                    }
                }
            }
        }
    }

    inner class GalleryAdapter(private val items: List<MediaItem>, private val onClick: (MediaItem) -> Unit) :
        RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
            val ivPlayIcon: ImageView = view.findViewById(R.id.ivPlayIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_media, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.ivPlayIcon.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            
            holder.ivThumbnail.setImageURI(item.uri) // Simple loading. In production, Glide/Coil is better.

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
