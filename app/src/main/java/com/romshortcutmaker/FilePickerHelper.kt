package com.romshortcutmaker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object FilePickerHelper {
    fun buildPickFileIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf(
                "application/octet-stream",
                "application/x-zip-compressed"
            )
        )
        return intent
    }

    fun uriToPath(context: Context, uri: Uri): String? {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val documentId = DocumentsContract.getDocumentId(uri)
                val parts = documentId.split(":", limit = 2)

                if (uri.authority == "com.android.externalstorage.documents" && parts.isNotEmpty()) {
                    val volumeId = parts[0]
                    val relativePath = parts.getOrNull(1).orEmpty()
                    val basePath = if (volumeId.equals("primary", ignoreCase = true)) {
                        "/storage/emulated/0"
                    } else {
                        "/storage/$volumeId"
                    }

                    return if (relativePath.isBlank()) {
                        basePath
                    } else {
                        "$basePath/$relativePath"
                    }
                }
            }

            val path = uri.path
            if (!path.isNullOrEmpty()) {
                path
            } else {
                uri.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildDolphinAutoStartFile(romUri: Uri?, romPath: String): String? {
        val treeParts = when {
            romUri != null && romUri.authority == "com.android.externalstorage.documents" -> {
                treePartsFromDocumentId(DocumentsContract.getDocumentId(romUri))
            }

            else -> treePartsFromPath(romPath)
        } ?: return null

        val driveId = treeParts.first
        val userPath = treeParts.second

        if (userPath.isBlank()) {
            return "content://com.android.externalstorage.documents/tree/${Uri.encode("$driveId:")}"
        }

        val normalizedUserPath = userPath.replace('\\', '/').trim('/')
        if (normalizedUserPath.isBlank()) {
            return "content://com.android.externalstorage.documents/tree/${Uri.encode("$driveId:")}"
        }

        val fileName = normalizedUserPath.substringAfterLast('/')
        val parentPath = normalizedUserPath.substringBeforeLast('/', "")
        val encodedTreePrefix = if (parentPath.isBlank()) {
            Uri.encode("$driveId:")
        } else {
            Uri.encode("$driveId:$parentPath")
        }
        return "content://com.android.externalstorage.documents/tree/$encodedTreePrefix/$fileName"
    }

    private fun treePartsFromDocumentId(documentId: String): Pair<String, String>? {
        val parts = documentId.split(":", limit = 2)
        if (parts.isEmpty() || parts[0].isBlank()) {
            return null
        }

        val driveId = parts[0]
        val relativePath = parts.getOrNull(1).orEmpty()
        return driveId to relativePath
    }

    private fun treePartsFromPath(romPath: String): Pair<String, String>? {
        val normalizedPath = romPath.replace('\\', '/')
        val storagePrefix = "/storage/"

        if (!normalizedPath.startsWith(storagePrefix, ignoreCase = true)) {
            return null
        }

        val storageRemainder = normalizedPath.substring(storagePrefix.length)
        val driveId = storageRemainder.substringBefore('/')
        val relativePath = storageRemainder.substringAfter('/', "")
        if (driveId.isBlank()) {
            return null
        }

        val volumeId = if (driveId.equals("emulated", ignoreCase = true)) {
            "primary"
        } else {
            driveId
        }

        return volumeId to relativePath
    }
}
