package ro.snapify.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

fun getVideoThumbnail(context: Context, uri: android.net.Uri): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        null
    } finally {
        // retriever.release() but it's in try
    }
}
