package com.apkupdateross.ui.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apkupdateross.R
import com.apkupdateross.util.getAppIcon

@Composable
private fun BaseLoadingImage(
    request: ImageRequest,
    modifier: Modifier,
    color: Color = Color.Transparent
) = AsyncImage(
    model = request,
    contentDescription = stringResource(R.string.app_cd),
    modifier = modifier
        .padding(0.dp)
        .background(color),
    contentScale = ContentScale.Fit,
    error = painterResource(R.drawable.ic_root),
    placeholder = painterResource(R.drawable.ic_empty)
)

@Composable
fun LoadingImage(
    uri: Uri,
    modifier: Modifier = Modifier.height(120.dp).fillMaxSize(),
    crossfade: Boolean = true,
    color: Color = Color.Transparent
) = BaseLoadingImage(
    ImageRequest.Builder(LocalContext.current).data(uri).crossfade(crossfade).build(),
    modifier,
    color
)

@Composable
fun LoadingImageApp(
    packageName: String,
    modifier: Modifier = Modifier.height(120.dp).fillMaxSize(),
    crossfade: Boolean = false,
    color: Color = Color.Transparent
) = BaseLoadingImage(
    ImageRequest.Builder(LocalContext.current).data(LocalContext.current.getAppIcon(packageName)).crossfade(crossfade).build(),
    modifier,
    color
)
