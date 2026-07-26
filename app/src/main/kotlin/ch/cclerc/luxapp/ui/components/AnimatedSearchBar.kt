package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun AnimatedSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    placeholderText: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isTextFieldDisabled: Boolean,
    onTapWhenDisabled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val capsule = RoundedCornerShape(50)
    val fieldStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = colors.label
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(LuxMaterials.capsuleFill(), capsule)
            .border(0.5.dp, colors.hairline, capsule)
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 20.dp)
            ) {
                BasicTextField(
                    value = searchText,
                    onValueChange = { newValue ->
                        onSearchTextChange(newValue)
                        if (newValue.isEmpty()) onClear() else onSearch()
                    },
                    enabled = !isTextFieldDisabled,
                    singleLine = true,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchText.isEmpty()) {
                                AnimatedContent(
                                    targetState = placeholderText,
                                    transitionSpec = {
                                        fadeIn(LuxSprings.Gentle) togetherWith fadeOut(LuxSprings.Gentle)
                                    }
                                ) { text ->
                                    BasicText(
                                        text = text,
                                        style = fieldStyle.copy(color = colors.tertiaryLabel),
                                        maxLines = 1
                                    )
                                }
                            }
                            innerTextField()
                        }
                    }
                )
            }
            if (searchText.isNotEmpty() && !isTextFieldDisabled) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .scaleClickable(haptic = false) { onClear() }
                ) {
                    SFSymbol(name = "xmark.circle.fill", size = 16.sp, color = colors.systemGray)
                }
            }
            Box(
                Modifier
                    .padding(end = 18.dp)
                    .then(
                        if (isTextFieldDisabled) Modifier
                        else Modifier.scaleClickable(haptic = false) { onSearch() }
                    )
            ) {
                SFSymbol(name = "magnifyingglass", size = 20.sp, color = accent)
            }
        }
        if (isTextFieldDisabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .scaleClickable { onTapWhenDisabled() }
            )
        }
    }
}
