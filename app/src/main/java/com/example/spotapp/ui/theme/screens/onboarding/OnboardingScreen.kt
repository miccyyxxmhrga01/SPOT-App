package com.example.spotapp.ui.theme.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spotapp.R
import com.example.spotapp.ui.theme.components.SpotButtonStyle
import com.example.spotapp.ui.theme.components.SpotGoldGradient
import com.example.spotapp.ui.theme.components.SpotLogoMedallion
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotPillButton
import com.example.spotapp.ui.theme.components.SpotWordmarkText
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.spotSp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class OnboardingPage(
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int,
    val imageDescription: String,
    val badge: String,
    val icon: ImageVector
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Showcase your talent",
        description = "Share your performances, artwork, photography, coding projects, and other skills with your campus community.",
        imageRes = R.drawable.onboarding_showcase,
        imageDescription = "Cartoon students dancing, singing, playing guitar, and painting in a golden spotlight",
        badge = "Show what you can do",
        icon = Icons.Rounded.Star
    ),
    OnboardingPage(
        title = "Get discovered",
        description = "Post your best moments and let the right students discover your talent across the SPOT community.",
        imageRes = R.drawable.onboarding_discover,
        imageDescription = "A cartoon singer being discovered by students through his talent profile",
        badge = "Find your audience",
        icon = Icons.Rounded.Search
    ),
    OnboardingPage(
        title = "Connect and grow",
        description = "Follow talented students, collaborate, receive feedback, and grow your campus network.",
        imageRes = R.drawable.onboarding_connect,
        imageDescription = "Four cartoon students sharing ideas around a phone, sketchbook, and camera",
        badge = "Create together",
        icon = Icons.Rounded.Groups
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == onboardingPages.lastIndex

    Box(
        Modifier.fillMaxSize().spotAmbientBackground(SpotPalette.Ivory)
            .statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            OnboardingTopBar(isLastPage, onSkip = onFinished)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 16.dp
            ) { page ->
                OnboardingPageContent(
                    page = onboardingPages[page],
                    modifier = Modifier.graphicsLayer {
                        // Follow the finger continuously instead of jumping when the selected page changes.
                        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        scaleX = 1f - .035f * distance
                        scaleY = scaleX
                        alpha = 1f - .2f * distance
                    }
                )
            }
            OnboardingBottomSection(
                currentPage = currentPage,
                pageCount = onboardingPages.size,
                isLastPage = isLastPage,
                enabled = !pagerState.isScrollInProgress
            ) {
                if (!pagerState.isScrollInProgress) {
                    if (isLastPage) onFinished()
                    else scope.launch {
                        pagerState.animateScrollToPage(
                            currentPage + 1,
                            animationSpec = tween(360, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(isLastPage: Boolean, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotLogoMedallion(34.dp, glow = false)
        Spacer(Modifier.width(9.dp))
        SpotWordmarkText(18.spotSp)
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(!isLastPage, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
            SpotPillButton(
                "Skip", onSkip, modifier = Modifier.heightIn(min = 48.dp),
                style = SpotButtonStyle.Secondary, compact = true
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().padding(top = 4.dp, bottom = 8.dp)) {
        val sideBySide = maxWidth >= 560.dp && maxWidth > maxHeight
        val compact = maxHeight < 460.dp

        if (sideBySide) {
            // Leave room for the artwork and readable copy when the phone is rotated.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                OnboardingStage(page, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(20.dp))
                OnboardingCopy(page, compact = true, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Measure text first, then fit the entire square illustration in the remaining height.
                OnboardingStage(page, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                OnboardingCopy(page, compact)
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            }
        }
    }
}

@Composable
private fun OnboardingCopy(page: OnboardingPage, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.background(SpotPalette.GoldSoft, CircleShape)
                .border(1.dp, SpotPalette.GoldLine, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(page.icon, null, Modifier.size(14.dp), tint = SpotPalette.GoldDeep)
            Spacer(Modifier.width(6.dp))
            Text(
                page.badge, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(
            page.title, Modifier.semantics { heading() },
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            color = SpotPalette.Ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            page.description, Modifier.widthIn(max = 360.dp).padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingStage(page: OnboardingPage, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(6.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier.size(side)
                .shadow(10.dp, shape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
                .clip(shape)
                .background(Color(0xFF17130D))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(SpotPalette.GoldBright.copy(alpha = .65f), SpotPalette.GoldDeep.copy(alpha = .22f))
                    ),
                    shape
                )
        ) {
            // Approved square PNGs already contain their spotlight; Fit preserves every character.
            Image(
                painterResource(page.imageRes), page.imageDescription,
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun OnboardingBottomSection(
    currentPage: Int,
    pageCount: Int,
    isLastPage: Boolean,
    enabled: Boolean,
    onButtonClick: () -> Unit
) {
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingPageIndicator(currentPage, pageCount)
        Spacer(Modifier.height(16.dp))
        SpotPillButton(
            if (isLastPage) "Enter SPOT" else "Continue", onButtonClick,
            Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = enabled,
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward
        )
    }
}

@Composable
private fun OnboardingPageIndicator(currentPage: Int, pageCount: Int) {
    Row(
        Modifier.semantics { stateDescription = "Page ${currentPage + 1} of $pageCount" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = currentPage == index
            val width by animateDpAsState(
                if (selected) 28.dp else 7.dp,
                spring(dampingRatio = .85f, stiffness = 450f), label = "onboarding indicator"
            )
            Box(
                Modifier.width(width).height(7.dp).clip(CircleShape)
                    .background(
                        if (selected) SpotGoldGradient
                        else SolidColor(if (index < currentPage) SpotPalette.GoldLine else SpotPalette.Line)
                    )
            )
        }
    }
}
