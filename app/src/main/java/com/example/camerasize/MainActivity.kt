package com.example.camerasize

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.os.Environment

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val MAX_WIDTH = 800
        private const val MAX_HEIGHT = 600
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonTakePhoto = findViewById<Button>(R.id.button_take_photo)
        buttonTakePhoto.setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private lateinit var currentPhotoPath: String

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Создайте файл, в который должно быть сохранено фото
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.camerasize.fileprovider",
                        it
                    )

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }


    private fun createImageFile(): File {
        // Создание имени файла с меткой времени
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: throw IOException("Не удалось получить директорию 'Pictures'")

        // Создание файла изображения
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = BitmapFactory.decodeFile(currentPhotoPath)

            // Сохраняем оригинальное изображение
            saveImageToGallery(imageBitmap, false)

            // Сжимаем и сохраняем сжатое изображение
            val compressedBitmap = compressImage(imageBitmap)
            saveImageToGallery(compressedBitmap, true)
        }
    }


    private fun saveImageToGallery(bitmap: Bitmap, isCompressed: Boolean) {
        val filename = "image_" + if (isCompressed) "compressed_" else "original_" + System.currentTimeMillis() + ".jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            } ?: run {
            }
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = uri
            sendBroadcast(intent)
        }

    }
    private fun compressImage(imageBitmap: Bitmap): Bitmap {
        val initialStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, initialStream)
        var byteArray = initialStream.toByteArray()

        // Проверяем начальный размер изображения
        val initialSizeInMb = byteArray.size / (1024 * 1024)
        if (initialSizeInMb <= 2) {
            return imageBitmap // Возвращаем исходное изображение, если оно уже меньше 2 МБ
        }

        // Начальное сжатие
        var quality = 100
        val stream = ByteArrayOutputStream()
        while (byteArray.size > 2 * 1024 * 1024 && quality > 0) {
            stream.reset()
            quality -= 1 // Меньший шаг может быть более точным
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            byteArray = stream.toByteArray()
        }

        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }


    // Функция для динамического подбора качества сжатия
    private fun adjustQuality(currentQuality: Int, currentSizeMb: Int): Int {
        val qualityDecreaseStep = if (currentSizeMb > 5) 10 else 5
        return (currentQuality - qualityDecreaseStep).coerceAtLeast(0)
    }



}
