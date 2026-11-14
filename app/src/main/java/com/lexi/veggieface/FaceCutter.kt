package com.lexi.veggieface

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max

/** The cut-out face, plus whether we actually found one (or just guessed the middle). */
class CutFace(val bitmap: Bitmap, val found: Boolean)

object FaceCutter {

    /** Loads a photo, turned the right way up and shrunk to a sensible size. */
    suspend fun load(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // This only reads the picture's size (decodeStream returns null here on purpose),
        // so give up only if the file can't be opened or isn't a picture.
        val opened = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null

        val rotation = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }

    /** Finds the biggest face and cuts it out along its outline with soft edges. */
    suspend fun cut(photo: Bitmap): CutFace {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(options)
        val faces = suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(photo, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
        detector.close()
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        return withContext(Dispatchers.Default) {
            val outline = Path()
            val box = RectF()
            val points = face?.getContour(FaceContour.FACE)?.points
            if (points != null && points.size > 5) {
                outline.moveTo(points[0].x, points[0].y)
                for (p in points.drop(1)) outline.lineTo(p.x, p.y)
                outline.close()
                outline.computeBounds(box, true)
            } else {
                // No face found: use an oval in the middle. The kid can still drag it into place.
                val r = if (face != null) RectF(face.boundingBox) else {
                    val side = minOf(photo.width, photo.height) * 0.6f
                    RectF((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
                }
                box.set(r)
                outline.addOval(r, Path.Direction.CW)
            }

            val pad = box.width() * 0.05f
            val out = Bitmap.createBitmap((box.width() + pad * 2).toInt().coerceAtLeast(1), (box.height() + pad * 2).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.translate(pad - box.left, pad - box.top)
            // 1. Draw the face shape with blurry edges...
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(box.width() * 0.035f, BlurMaskFilter.Blur.NORMAL) }
            canvas.drawPath(outline, mask)
            // 2. ...then paint the photo only where that shape is.
            val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
            canvas.drawBitmap(photo, 0f, 0f, photoPaint)
            CutFace(out, face != null)
        }
    }

    /** A fresh file the camera app can save into. */
    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "com.lexi.veggieface.files", file)
    }

    /** Saves the picture into the phone's gallery (Pictures/Veggie Face). */
    suspend fun saveToGallery(context: Context, bmp: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "veggieface_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Veggie Face")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    }

    /** Opens the share sheet (WhatsApp, Messages, …) with the picture. */
    suspend fun share(context: Context, bmp: Bitmap) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "veggieface_share.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "com.lexi.veggieface.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your Veggie Face"))
    }
}
