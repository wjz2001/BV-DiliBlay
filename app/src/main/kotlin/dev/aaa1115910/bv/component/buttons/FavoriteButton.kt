package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.component.FavoriteDialog
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun FavoriteButton(
    modifier: Modifier = Modifier,
    isFavorite: Boolean,
    countText: String = "",
    userFavoriteFolders: List<FavoriteFolderMetadata> = emptyList(),
    favoriteFolderIds: List<Long> = emptyList(),
    onAddToDefaultFavoriteFolder: () -> Unit,
    onUpdateFavoriteFolders: (List<Long>) -> Unit
) {
    var showFavoriteDialog by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(percent = 50)

    Button(
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(0.dp),
        scale = ButtonDefaults.scale(
            focusedScale = 1f,
            pressedScale = 1f
        ),
        shape = ButtonDefaults.shape(
            shape = pillShape,
            focusedShape = pillShape,
            pressedShape = pillShape,
            disabledShape = pillShape,
            focusedDisabledShape = pillShape
        ),
        onClick = {
            if (showFavoriteDialog) return@Button
            showFavoriteDialog = true
        }
    ) {
        CapsuleStatButtonContent(
            icon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null
                )
            },
            text = countText
        )
    }

    FavoriteDialog(
        show = showFavoriteDialog,
        onHideDialog = { showFavoriteDialog = false },
        userFavoriteFolders = userFavoriteFolders,
        favoriteFolderIds = favoriteFolderIds,
        onUpdateFavoriteFolders = onUpdateFavoriteFolders
    )
}

@Preview
@Composable
fun FavoriteButtonEnablePreview() {
    BVTheme {
        FavoriteButton(
            isFavorite = true,
            onAddToDefaultFavoriteFolder = {},
            onUpdateFavoriteFolders = {}
        )
    }
}

@Preview
@Composable
fun FavoriteButtonDisablePreview() {
    BVTheme {
        FavoriteButton(
            isFavorite = false,
            onAddToDefaultFavoriteFolder = {},
            onUpdateFavoriteFolders = {}
        )
    }
}