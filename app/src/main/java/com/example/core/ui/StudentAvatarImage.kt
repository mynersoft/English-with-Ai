package com.example.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import java.io.File

@Composable
fun StudentAvatarImage(
    photoUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val cleanUrl = photoUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    val fallbackPainter = painterResource(id = R.drawable.img_student_avatar)

    if (cleanUrl == null) {
        Image(
            painter = fallbackPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    if (AvatarImageHelper.isDataImageUri(cleanUrl)) {
        val decodedBitmap = remember(cleanUrl) {
            AvatarImageHelper.decodeDataUriBitmap(cleanUrl)
        }
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            Image(
                painter = fallbackPainter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        return
    }

    val context = LocalContext.current
    val modelData: Any = remember(cleanUrl) {
        if (cleanUrl.startsWith("/")) File(cleanUrl) else cleanUrl
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(modelData)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        fallback = fallbackPainter,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
