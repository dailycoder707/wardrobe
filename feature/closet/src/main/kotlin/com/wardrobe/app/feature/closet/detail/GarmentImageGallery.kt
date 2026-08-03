package com.wardrobe.app.feature.closet.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.garment.ImageMetadata
import com.wardrobe.app.core.model.garment.ImageType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGallery(
    images: List<ImageMetadata>,
    modifier: Modifier = Modifier,
) {
    val displayable =
        images
            .filter { it.type == ImageType.CUTOUT || it.type == ImageType.ORIGINAL }
            .ifEmpty { images }
    if (displayable.isEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {}
        return
    }

    var isViewerOpen by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { displayable.size })

    Column(modifier = modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            AsyncImage(
                model = displayable[page].filePath,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable { isViewerOpen = true },
            )
        }
        if (displayable.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayable) { image ->
                    AsyncImage(
                        model = image.filePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }

    if (isViewerOpen) {
        FullScreenImageViewer(images = displayable, initialPage = pagerState.currentPage, onDismiss = {
            isViewerOpen =
                false
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenImageViewer(
    images: List<ImageMetadata>,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onDismiss),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = images[page].filePath,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
