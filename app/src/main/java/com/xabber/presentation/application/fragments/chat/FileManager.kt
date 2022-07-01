package com.xabber.presentation.application.fragments.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.xabber.BuildConfig
import com.xabber.R
import com.xabber.presentation.BaseFragment
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*

class FileManager: Fragment() {
    companion object {
        private val LOG_TAG: String = FileManager::class.java.simpleName
        private val VALID_IMAGE_EXTENSIONS = arrayOf("webp", "jpeg", "jpg", "png", "jpe", "gif")
        val instance: FileManager? = null
        private const val XABBER_DIR = "Xabber"
        private const val XABBER_AUDIO_DIR = "Xabber Audio"


        fun fileIsImage(file: File): Boolean {
            return extensionIsImage(extractRelevantExtension(file.path))
        }

        fun extensionIsImage(extension: String?): Boolean {
            return Arrays.asList(*VALID_IMAGE_EXTENSIONS).contains(extension)
        }

        fun isImageUrl(text: String?): Boolean {
            if (text == null) {
                return false
            }
            return if (text.trim { it <= ' ' }.contains(" ")) {
                false
            } else try {
                val url = URL(text)
                if (!url.protocol.equals("http", ignoreCase = true)
                    && !url.protocol.equals("https", ignoreCase = true)
                ) {
                    return false
                }
                val extension = extractRelevantExtension(url) ?: return false
                extensionIsImage(extension)
            } catch (e: MalformedURLException) {
                false
            }
        }

        fun extractFileName(uri: String?): String? {
            return if (uri == null || uri.isEmpty()) {
                null
            } else uri.substring(uri.lastIndexOf('/') + 1)
                .toLowerCase()
        }

        private fun extractRelevantExtension(url: URL): String? {
            val path = url.path
            return extractRelevantExtension(path)
        }

        private fun extractRelevantExtension(path: String?): String? {
            if (path == null || path.isEmpty()) {
                return null
            }
            val filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase()
            val dotPosition = filename.lastIndexOf(".")
            return if (dotPosition != -1) {
                filename.substring(dotPosition + 1).toLowerCase()
            } else null
        }


       fun getFileUri(file: File, context: Context): Uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file)

//        fun isImageSizeGreater(srcUri: Uri?, maxSize: Int): Boolean {
//            val srcPath: String =
//                FileUtils.getPath(Application.getInstance(), srcUri) ?: return false
//            val fis: FileInputStream
//            fis = try {
//                FileInputStream(srcPath)
//            } catch (e: FileNotFoundException) {
//                return false
//            }
//            val options = BitmapFactory.Options()
//            options.inJustDecodeBounds = true
//            BitmapFactory.decodeStream(fis, null, options)
//            try {
//                fis.close()
//            } catch (e: IOException) {
//            }
//            return options.outHeight > maxSize || options.outWidth > maxSize
//        }

//        fun isImageNeedRotation(srcUri: Uri?): Boolean {
//            val srcPath: String =
//                FileUtils.getPath(Application.getInstance(), srcUri) ?: return false
//            val exif: ExifInterface
//            exif = try {
//                ExifInterface(srcPath)
//            } catch (e: IOException) {
//                LogManager.exception(LOG_TAG, e)
//                return false
//            }
//            val orientation = exif.getAttributeInt(
//                ExifInterface.TAG_ORIENTATION,
//                ExifInterface.ORIENTATION_NORMAL
//            )
//            return when (orientation) {
//                ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> true
//                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> false
//                else -> false
//            }
//        }
//
//        fun isImageNeededDimensionsFlip(srcUri: Uri?): Boolean {
//            val srcPath: String =
//                FileUtils.getPath(Application.getInstance(), srcUri) ?: return false
//            val exif: ExifInterface
//            exif = try {
//                ExifInterface(srcPath)
//            } catch (e: IOException) {
//                LogManager.exception(LOG_TAG, e)
//                return false
//            }
//            val orientation = exif.getAttributeInt(
//                ExifInterface.TAG_ORIENTATION,
//                ExifInterface.ORIENTATION_NORMAL
//            )
//            return when (orientation) {
//                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> true
//                ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> false
//                else -> false
//            }
//        }
//
//        fun saveImage(data: ByteArray?, fileName: String?): Uri? {
//            val rotateImageFile: File
//            var bos: BufferedOutputStream? = null
//            try {
//                rotateImageFile = createTempImageFile(fileName)
//                bos = BufferedOutputStream(FileOutputStream(rotateImageFile))
//                bos.write(data)
//            } catch (e: IOException) {
//                LogManager.exception(LOG_TAG, e)
//                return null
//            } finally {
//                if (bos != null) {
//                    try {
//                        bos.flush()
//                        bos.close()
//                    } catch (e: IOException) {
//                        LogManager.exception(LOG_TAG, e)
//                    }
//                }
//            }
//            return getFileUri(rotateImageFile)
//        }

//        fun savePNGImage(data: ByteArray?, fileName: String?): Uri? {
//            val rotateImageFile: File
//            var bos: BufferedOutputStream? = null
//            try {
//                rotateImageFile = createTempPNGImageFile(fileName)
//                bos = BufferedOutputStream(FileOutputStream(rotateImageFile))
//                bos.write(data)
//            } catch (e: IOException) {
//
//                return null
//            } finally {
//                if (bos != null) {
//                    try {
//                        bos.flush()
//                        bos.close()
//                    } catch (e: IOException) {
//
//                    }
//                }
//            }
//            return getFileUri(rotateImageFile)
//        }

        fun getFileUri(context: Context, file: File?): Uri {
            return FileProvider.getUriForFile(
               context,
                "Xabber" + ".provider",
                file!!
            )
        }

//        @Throws(IOException::class)
//        fun createTempImageFile(name: String?): File {
//            // Create an image file name
//            return File.createTempFile(
//                name,  /* prefix */
//                ".jpg",  /* suffix */
//                Application.getInstance().getExternalFilesDir(null) /* directory */
//            )
//        }

//        @Throws(IOException::class)
//        fun createTempOpusFile(name: String?): File {
//            return File.createTempFile(name, ".opus", Application.getInstance().getCacheDir())
//        }

        /**
         * Makes a copy of the source file and then deletes it.
         *
         * @param source file that will be copied and subsequently deleted
         * @param dest   file the data will be copied to
         * @return success of copying
         */
        fun copy(source: File, dest: File?): Boolean {
            var success = true
            try {
                val `in`: InputStream = FileInputStream(source)
                try {
                    val out: OutputStream = FileOutputStream(dest)
                    try {
                        val buf = ByteArray(1024)
                        var len: Int
                        while (`in`.read(buf).also { len = it } > 0) {
                            out.write(buf, 0, len)
                        }
                    } finally {
                        out.close()
                    }
                } finally {
                    `in`.close()
                }
            } catch (e: IOException) {
                success = false
            }
            if (success) deleteTempFile(source)
            return success
        }

        fun deleteTempFile(fileOrig: File) {
            if (fileOrig.exists()) {
                fileOrig.delete()
            }
        }

        fun deleteTempFile(filePath: String?) {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        }

//        @Throws(IOException::class)
//        fun createTempPNGImageFile(name: String?): File {
//            // Create an image file name
//            return File.createTempFile(
//                name,  /* prefix */
//                ".png",  /* suffix */
//                Application.getInstance().getExternalFilesDir(null) /* directory */
//            )
//        }

//        fun getIntentForShareFile(file: File): Intent {
//            val intent = Intent(Intent.ACTION_SEND)
//            intent.putExtra(Intent.EXTRA_STREAM, getFileUri(file))
//            intent.type = HttpFileUploadManager.getMimeType(file.path)
//            intent.putExtra(Intent.EXTRA_TEXT, file.name)
//            return intent
//        }

        /**
         * For java 6
         */
        fun deleteDirectoryRecursion(file: File) {
            if (file.isDirectory) {
                val entries = file.listFiles()
                if (entries != null) {
                    for (entry in entries) {
                        deleteDirectoryRecursion(entry)
                    }
                }
            }
            if (!file.delete()) {
                Log.d(LOG_TAG, "Failed to delete $file")
            }
        }

//        fun generateUniqueNameForFile(path: String, sourceName: String?): String {
//            val extension: String = FilenameUtils.getExtension(sourceName)
//            val baseName: String = FilenameUtils.getBaseName(sourceName)
//            var i = 0
//            var newName: String
//            var file: File
//            do {
//                // limitation to prevent infinite loop
//                if (i > 200) return UUID.randomUUID().toString() + "." + extension
//                i++
//                newName = "$baseName($i).$extension"
//                file = File(path + newName)
//            } while (file.exists())
//            return newName
//        }
//
//        init {
//            instance = FileManager()
//        }
//    }

         fun createTempOpusFile(name: String, context: Context) : File {
        return File.createTempFile(name, ".opus", context.cacheDir)
    }



//        fun createAudioFile(name: String?): File? {
//            // create dir
//            var directory = File(downloadDirPath)
//            if (!directory.exists()) {
//                if (!directory.mkdir()) {
//
//                    return null
//                }
//            }
//            directory = File(specificDownloadDirPath)
//            if (!directory.exists()) {
//                if (!directory.mkdir()) {
//                    return null
//                }
//            }
//
//            // create file
////            val filePath = (directory.path + File.separator
////                    + FilenameUtils.getBaseName(name) + "_"
////                    + System.currentTimeMillis() / 1000 + "."
////                    + FilenameUtils.getExtension(name))
////            var file = File(filePath)
////            if (file.exists()) {
////                file = File(
////                    directory.path + File.separator +
////                            generateUniqueNameForFile(
////                                directory.path
////                                        + File.separator, name
////                            )
////                )
//                return file
//            }
//            return file
//        }
//
//        private val downloadDirPath: String
//            private get() = Application.getInstance().getExternalFilesDir(null).getPath()
//                .toString() + File.separator + XABBER_DIR
//        private val specificDownloadDirPath: String
//            private get() = downloadDirPath + File.separator + XABBER_AUDIO_DIR
//
//        fun deleteFile(file: File?): Boolean {
//            var deletedAll = true
//            if (file != null) {
//                if (file.isDirectory) {
//                    val children = file.list()
//                    if (children != null) {
//                        for (child in children) {
//                            deletedAll = deleteFile(File(file, child)) && deletedAll
//                        }
//                    }
//                } else {
//                    deletedAll = file.delete()
//                }
//            }
//            return deletedAll
//        }
   }
}

