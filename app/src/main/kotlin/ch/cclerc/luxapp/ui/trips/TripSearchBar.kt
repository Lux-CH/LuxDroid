package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.SelectedLocation
import ch.cclerc.luxcom.model.SearchResult

@Composable
fun TripSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    placeholderText: String,
    selectedLocation: SelectedLocation?,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveTag: () -> Unit = {},
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    shortcutSymbol: (SearchResult) -> String? = { null }
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val fieldStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = colors.label
    )

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedLocation != null) {
            LocationTagView(
                location = selectedLocation,
                onRemove = onRemoveTag,
                modifier = Modifier.weight(1f, fill = false),
                shortcutSymbol = shortcutSymbol
            )
        } else {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = searchText,
                    onValueChange = { newValue ->
                        onSearchTextChange(newValue)
                        if (newValue.isEmpty()) onClear()
                    },
                    singleLine = true,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchText.isNotEmpty()) {
                                onSearch()
                                HapticFeedback.lightImpact()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocused() },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchText.isEmpty()) {
                                BasicText(
                                    text = placeholderText,
                                    style = fieldStyle.copy(color = colors.tertiaryLabel),
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        if (selectedLocation == null) {
            AnimatedVisibility(
                visible = searchText.isNotEmpty(),
                enter = scaleIn(LuxSprings.Select) + fadeIn(LuxSprings.Select),
                exit = scaleOut(LuxSprings.Select) + fadeOut(LuxSprings.Select)
            ) {
                Box(
                    Modifier
                        .padding(end = 35.dp)
                        .scaleClickable(haptic = false) {
                            onSearchTextChange("")
                            onClear()
                        }
                ) {
                    SFSymbol(name = "xmark.circle.fill", size = 16.sp, color = colors.systemGray)
                }
            }
        }
    }
}
