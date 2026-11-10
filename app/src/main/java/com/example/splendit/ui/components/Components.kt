package com.example.splendit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.splendit.R
import com.example.splendit.ui.theme.Sw

@Composable
fun SwIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/** Initials on a tinted shape, optionally covered by a photo (group pictures). */
@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: Shape = CircleShape,
    background: Color = Sw.colors.accent800,
    contentColor: Color = Sw.colors.accent200,
    fontSize: TextUnit = 13.sp,
    photoUri: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = contentColor, fontSize = fontSize, fontWeight = FontWeight.Medium)
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Sw.colors.divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SwIcon(icon, size = 18.dp, contentDescription = contentDescription)
    }
}

/** Centered title with a back button, used by the full-screen flows. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes backIcon: Int = R.drawable.ic_back,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(backIcon, "Back", onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = Sw.colors.neutral500)
                }
            }
        }
        Spacer(Modifier.width(40.dp))
    }
}

/** Single-line text input styled like the prototype's search and form fields. */
@Composable
fun SwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    prefix: String? = null,
    background: Color = MaterialTheme.colorScheme.surface,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
    height: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    trailing: (@Composable () -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = 14.sp),
        cursorBrush = SolidColor(Sw.colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .height(height)
            .shadow(0.5.dp, shape)
            .clip(shape)
            .background(background),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (leadingIcon != null) {
                    SwIcon(leadingIcon, size = 18.dp, tint = Sw.colors.neutral500)
                }
                if (prefix != null) Text(prefix, fontSize = 14.sp, color = Sw.colors.neutral400)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = Sw.colors.neutral600)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        }
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Sw.colors.accent400,
            contentColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Outlined accent pill (`.btn-primary` in the prototype). */
@Composable
fun AccentOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, Sw.colors.accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sw.colors.accent),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        if (icon != null) {
            SwIcon(icon, size = 15.dp, tint = Sw.colors.accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp)
    }
}

/** Round check mark used for selections. */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val ring = if (checked) Sw.colors.accent400 else Sw.colors.neutral700
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) Sw.colors.accent400 else Color.Transparent)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            SwIcon(
                R.drawable.ic_check,
                size = size * 0.55f,
                tint = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    val bg = if (accent) Sw.colors.accent800 else Sw.colors.neutral800
    val fg = if (accent) Sw.colors.accent100 else Sw.colors.neutral100
    Text(
        text = text,
        fontSize = 11.sp,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** Hairline that fades out at both ends — the prototype's divider signature. */
@Composable
fun FadeDivider(modifier: Modifier = Modifier) {
    val c = Sw.colors.divider
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(listOf(Color.Transparent, c, c, Color.Transparent))
            )
    )
}

/** Segmented pill selector (filters, invite tabs, split modes). */
@Composable
fun <T> SegmentedPills(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    selectedBackground: Color = Sw.colors.accent400,
    selectedContent: Color = MaterialTheme.colorScheme.surface,
    trackColor: Color = MaterialTheme.colorScheme.surface
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(trackColor)
            .padding(3.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                text = label,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                color = if (on) selectedContent else Sw.colors.neutral400,
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .clip(CircleShape)
                    .background(if (on) selectedBackground else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
