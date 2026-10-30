package com.example.spotapp.ui.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.R
import com.example.spotapp.data.AuthRepository
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.spotSp

// Shared by onboarding, sign-in and sign-up so the whole entry flow reads as one brand moment.

private val AuthInk get() = SpotPalette.Ink
private val AuthMuted get() = SpotPalette.Muted
private val AuthGoldDark get() = SpotPalette.GoldInk
private val MedallionRing get() = Brush.sweepGradient(listOf(SpotPalette.GoldBright, SpotPalette.GoldDeep, Color(0xFFE8B84B), SpotPalette.GoldBright))

/** The SPOT logo on a white medallion with a gold ring and an optional soft glow. */
@Composable
fun SpotLogoMedallion(size: Dp, modifier: Modifier = Modifier, glow: Boolean = true, contentDescription: String? = "SPOT logo") {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // The glow is drawn outside the medallion's bounds, so it never changes the layout.
        if (glow) Box(Modifier.matchParentSize().scale(1.45f)
            .background(Brush.radialGradient(listOf(SpotPalette.GoldBright.copy(alpha = .5f), Color.Transparent)), CircleShape))
        Box(Modifier.matchParentSize().shadow(size * .1f, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(MedallionRing, CircleShape).padding(size * .05f).background(SpotPalette.Surface, CircleShape),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.spotlogo), contentDescription, Modifier.fillMaxSize(.74f))
        }
    }
}

/** "SPOT" with the gold O, matching the home header. */
@Composable
fun SpotWordmarkText(fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = AuthInk) {
    Text(buildAnnotatedString {
        append("SP")
        withStyle(SpanStyle(color = SpotPalette.GoldDeep)) { append("O") }
        append("T")
    }, modifier, color = color, fontFamily = InterFontFamily, fontWeight = FontWeight.Black, fontSize = fontSize,
        letterSpacing = 2.sp, maxLines = 1)
}

/** Primary action for entry screens: a gold gradient pill that flattens while disabled or busy. */
@Composable
internal fun SpotAuthButton(text: String, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val active = enabled && !loading
    val shape = RoundedCornerShape(50)
    Button(
        onClick = onClick, enabled = active,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .shadow(if (active) 6.dp else 0.dp, shape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(if (active) SpotGoldGradient else SolidColor(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEFE6C8)), shape),
        shape = shape,
        border = BorderStroke(1.dp, if (active) Color.White.copy(alpha = .55f) else Color.Transparent),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SpotPalette.OnGold,
            disabledContainerColor = Color.Transparent, disabledContentColor = AuthMuted)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = AuthInk, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 16.spotSp, fontWeight = FontWeight.Bold)
    }
}

/** Quiet outlined pill for the action beside a primary [SpotAuthButton], such as "Resend code". */
@Composable
internal fun SpotAuthSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(50), border = BorderStroke(1.dp, SpotPalette.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = SpotPalette.Surface, contentColor = AuthInk,
            disabledContainerColor = SpotPalette.Surface, disabledContentColor = AuthMuted)
    ) { Text(text, fontSize = 15.spotSp, fontWeight = FontWeight.SemiBold) }
}

/** Warm ivory fields with gold focus, shared by every entry form. */
@Composable
internal fun spotAuthFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthInk, unfocusedTextColor = AuthInk, disabledTextColor = AuthMuted,
    focusedContainerColor = SpotPalette.Surface, unfocusedContainerColor = SpotPalette.Ivory, disabledContainerColor = SpotPalette.Ivory,
    focusedBorderColor = SpotPalette.GoldDeep, unfocusedBorderColor = SpotPalette.Line, disabledBorderColor = SpotPalette.Line,
    focusedLabelColor = AuthGoldDark, unfocusedLabelColor = AuthMuted, cursorColor = SpotPalette.GoldDeep,
    focusedLeadingIconColor = SpotPalette.GoldDeep, unfocusedLeadingIconColor = SpotPalette.GoldDeep,
    disabledLeadingIconColor = AuthMuted, focusedTrailingIconColor = AuthInk, unfocusedTrailingIconColor = AuthMuted,
    focusedPlaceholderColor = AuthMuted, unfocusedPlaceholderColor = AuthMuted
)

internal val SpotAuthFieldShape = RoundedCornerShape(16.dp)

/** A password field with a show/hide eye. Pages share one [visible] flag, so one tap reveals every field. */
@Composable
internal fun SpotPasswordField(value: String, onValueChange: (String) -> Unit, label: String, visible: Boolean,
    onToggleVisible: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, error: String? = null,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text(label) }, isError = error != null, supportingText = error?.let { { Text(it) } },
        shape = SpotAuthFieldShape, colors = spotAuthFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onToggleVisible, enabled = enabled) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide password" else "Show password")
        } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }))
}

/** Each new-password rule, ticked off live as the member types. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotPasswordChecklist(password: String, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().testTag("password-checklist"), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AuthRepository.passwordChecks(password).forEach { (rule, met) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (met) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (met) "Done" else "Not yet",
                    Modifier.size(16.dp), tint = if (met) SpotPalette.GreenInk else AuthMuted)
                Text(rule, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (met) AuthInk else AuthMuted)
            }
        }
    }
}

/**
 * New password, its confirmation and the live checklist, with Save pinned above the keyboard.
 * Change password and Reset password both use it; [top] holds what comes first (current password or emailed code).
 */
@Composable
internal fun SpotNewPasswordForm(intro: String, newPassword: String, onNewPassword: (String) -> Unit,
    confirm: String, onConfirm: (String) -> Unit, visible: Boolean, onToggleVisible: () -> Unit,
    busy: Boolean, error: String, saveLabel: String, ready: Boolean, onSave: () -> Unit,
    modifier: Modifier = Modifier, top: @Composable ColumnScope.() -> Unit = {}) {
    val mismatch = confirm.isNotEmpty() && confirm != newPassword
    Column(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(intro, style = MaterialTheme.typography.bodyMedium, color = AuthMuted)
            top()
            SpotPasswordField(newPassword, onNewPassword, "New password", visible, onToggleVisible,
                Modifier.testTag("new-password"), enabled = !busy)
            SpotPasswordChecklist(newPassword, Modifier.padding(start = 4.dp))
            SpotPasswordField(confirm, onConfirm, "Confirm password", visible, onToggleVisible,
                Modifier.testTag("confirm-password"), enabled = !busy, error = if (mismatch) "Passwords don't match" else null,
                imeAction = ImeAction.Done, onDone = { if (ready && !busy) onSave() })
            if (error.isNotBlank()) Text(error, Modifier.testTag("password-error").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag("save-password")) {
            SpotAuthButton(saveLabel, loading = busy, enabled = ready, onClick = onSave)
        }
    }
}
