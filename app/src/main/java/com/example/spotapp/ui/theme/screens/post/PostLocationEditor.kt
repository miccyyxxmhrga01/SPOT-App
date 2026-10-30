package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.post.PostLocationRepository
import com.example.spotapp.data.post.PostLocationSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

private data class PlaceSearchState(val query: String = "", val loading: Boolean = false,
    val results: List<PostLocationSuggestion> = emptyList(), val message: String? = null)

@Composable
internal fun PostLocationEditor(value: String, update: (String) -> Unit, done: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { PostLocationRepository(context) }
    val focus = LocalFocusManager.current
    val query = value.trim()
    var retry by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf(PlaceSearchState()) }
    val current = search.takeIf { it.query == query } ?: PlaceSearchState(query)
    fun choose(label: String) { update(label.take(160)); focus.clearFocus(); done() }

    LaunchedEffect(query, retry) {
        search = PlaceSearchState(query)
        if (query.length < 3) return@LaunchedEffect
        search = PlaceSearchState(query, loading = true)
        delay(650)
        search = try { PlaceSearchState(query, results = repository.search(query)) }
        catch (_: TimeoutCancellationException) { PlaceSearchState(query, message = "Place search took too long. Retry or use the name you typed.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PlaceSearchState(query, message = "Suggestions are unavailable. You can still use the name you typed.") }
    }

    Column(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Find a campus, city or place.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
        OutlinedTextField(value, { update(it.take(160)) }, Modifier.fillMaxWidth().testTag("post-location"),
            placeholder = { Text("Search places") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Outlined.Search, null, tint = PostGold) },
            trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { update("") }) { Icon(Icons.Outlined.Close, "Clear location") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { retry++ }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PostGold, unfocusedBorderColor = PostLine,
                focusedContainerColor = SpotPalette.Surface, unfocusedContainerColor = SpotPalette.Surface, cursorColor = PostGold))
        if (current.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("post-location-loading"), color = PostGold, trackColor = PostLine)
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("post-location-results"),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (query.length < 3) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = PostMuted)
                    Text("Type at least 3 characters to find places. Adding a city can help narrow the results.",
                        style = MaterialTheme.typography.bodySmall, color = PostMuted)
                }
            }
            current.message?.let { message -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = PostMuted)
                    TextButton(onClick = { retry++ }) { Text("Retry search", color = PostGold) }
                }
            } }
            items(current.results, key = { it.label }) { place ->
                Surface(shape = RoundedCornerShape(14.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, PostLine)) {
                    Row(Modifier.fillMaxWidth().clickable { choose(place.label) }.testTag("post-location-suggestion")
                        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Place, null, tint = PostGold, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (place.subtitle.isNotBlank()) Text(place.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = PostMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Outlined.Add, "Use this place", Modifier.size(18.dp), tint = PostMuted)
                    }
                }
            }
            if (query.length >= 3 && !current.loading && current.message == null && current.results.isEmpty()) item {
                Text("No matching places. Try a more complete name or keep your own location.",
                    Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = PostMuted)
            }
            if (query.isNotBlank()) item {
                TextButton(onClick = { choose(query) }, modifier = Modifier.fillMaxWidth().testTag("post-use-typed-location"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                    Icon(Icons.Outlined.EditLocationAlt, null, Modifier.size(20.dp), tint = PostGold)
                    Spacer(Modifier.width(10.dp))
                    Text("Use “$query”", style = MaterialTheme.typography.labelLarge, color = PostGold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
