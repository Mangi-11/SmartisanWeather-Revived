package com.smartisan.weather.ui.search

import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.data.model.HotCity
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.ui.components.OriginalIconButton
import com.smartisan.weather.ui.components.OriginalTextButton
import com.smartisan.weather.ui.components.PixelText
import com.smartisan.weather.ui.components.collectWeatherPressedAsState
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherScreenFrame
import com.smartisan.weather.ui.components.weatherDrawableBackground

@Composable
internal fun SearchCityScreen(
    state: SearchUiState,
    locationName: String? = null,
    locationKey: String? = null,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCityClick: (SearchResultCity) -> Unit,
    onHotCityClick: (HotCity) -> Unit,
    onLocationClick: () -> Unit,
    onAlreadyAdded: () -> Unit,
    requestKeyboard: Boolean = true,
) {
    WeatherScreenFrame(backgroundRes = R.drawable.list_bg) {
        Column(Modifier.fillMaxSize()) {
            SearchField(state.query, onQueryChange, onCancel, requestKeyboard)
            Box(Modifier.weight(1f).then(if (state.query.isNotBlank()) Modifier.imePadding() else Modifier)) {
                when {
                    state.query.isBlank() -> HotCities(state, locationName, locationKey, onHotCityClick, onLocationClick, onAlreadyAdded)
                    state.isLoading && state.results.isEmpty() -> SearchLoading(Modifier.align(Alignment.Center))
                    state.results.isNotEmpty() -> SearchResults(state, onCityClick, onAlreadyAdded)
                    else -> SearchMessage(state.isError, onRetry, Modifier.align(Alignment.Center))
                }
                WeatherDrawable(R.drawable.secondary_bar_shadow, null, Modifier.fillMaxWidth().height(6.dp))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onCancel: () -> Unit, requestKeyboard: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val cursorColor = remember(context, configuration) {
        // The XML editor inherited the platform cursor tint from this theme attribute.
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlActivated))
        try {
            Color(attributes.getColor(0, android.graphics.Color.BLACK))
        } finally {
            attributes.recycle()
        }
    }
    Row(
        Modifier.fillMaxWidth().background(colorResource(R.color.app_top_bar_background)).padding(6.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(32.dp).weatherDrawableBackground(R.drawable.search_bar_edit_bg_selector),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherDrawable(R.drawable.search_bar_left_icon_selector, null, Modifier.padding(start = 6.dp).size(24.dp, 30.dp))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).padding(start = 6.dp).focusRequester(focusRequester).testTag("search_query"),
                textStyle = TextStyle(color = colorResource(R.color.editor_text_color), fontSize = 15.sp, platformStyle = PlatformTextStyle(includeFontPadding = true)),
                cursorBrush = SolidColor(cursorColor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                decorationBox = { editor ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText(
                            stringResource(R.string.hint_edittext_add_city),
                            style = TextStyle(color = colorResource(R.color.editor_hint_text_color), fontSize = 15.sp),
                        )
                        editor()
                    }
                },
            )
            if (query.isNotEmpty()) {
                OriginalIconButton(R.drawable.selector_small_icon_btn_text_clear, stringResource(R.string.search_bar_clear), Modifier.size(30.dp)) {
                    onChange("")
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            } else Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(6.dp))
        OriginalIconButton(R.drawable.standard_icon_cancel_selector, stringResource(R.string.cancel), Modifier.size(36.dp), onClick = onCancel)
    }
    LaunchedEffect(requestKeyboard) {
        if (requestKeyboard) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}

@Composable
private fun HotCities(
    state: SearchUiState,
    locationName: String?,
    locationKey: String?,
    onHotCityClick: (HotCity) -> Unit,
    onLocationClick: () -> Unit,
    onAlreadyAdded: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var lastSelection by remember { mutableLongStateOf(0L) }
    fun select(action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSelection <= 500L) return
        lastSelection = now
        action()
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.isScrollInProgress }.collect { if (it) keyboard?.hide() } }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        PixelText(stringResource(R.string.weather_hot_city), 14.dp, colorResource(R.color.app_section_title_color),
            Modifier.padding(start = 24.dp, top = 24.dp), FontWeight.Bold)
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp)) {
            CityChip(locationName ?: stringResource(R.string.weather_search_city_default_locaiton),
                R.drawable.selector_location_city_item, locationKey in state.addedKeys) {
                // An existing location city can be relocated or upgraded to precise access.
                select(onLocationClick)
            }
            FlowRow(horizontalArrangement = Arrangement.Start, verticalArrangement = Arrangement.Top) {
                state.hotCities.forEach { city ->
                    val label = if (city.county.isBlank() || city.county == city.city) city.city
                    else city.city + stringResource(R.string.separator) + city.county
                    CityChip(label, R.drawable.selector_hot_city_item, city.cityId in state.addedKeys) {
                        select { if (city.cityId in state.addedKeys) onAlreadyAdded() else onHotCityClick(city) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityChip(label: String, background: Int, added: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectWeatherPressedAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val drawable = remember(context, background) { requireNotNull(ContextCompat.getDrawable(context, background)) }
    val padding = remember(drawable, density) {
        val bounds = Rect()
        drawable.getPadding(bounds)
        with(density) { PaddingValues(bounds.left.toDp(), bounds.top.toDp(), bounds.right.toDp(), bounds.bottom.toDp()) }
    }
    val minWidth = with(density) { drawable.minimumWidth.toDp() }
    val minHeight = with(density) { drawable.minimumHeight.toDp() }
    Box(
        Modifier.widthIn(min = minWidth).heightIn(min = minHeight)
            .weatherDrawableBackground(background, pressed = pressed, selected = added)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(label, 13.dp, colorResource(if (added) R.color.weather_hot_city_item_added_text_color else R.color.weather_hot_city_item_default_text_color),
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onCityClick: (SearchResultCity) -> Unit, onAlreadyAdded: () -> Unit) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.collect { if (it) keyboard?.hide() } }
    val elastic = rememberSearchElasticState()
    Box(Modifier.fillMaxSize().clipToBounds().searchElasticContainer(elastic)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().searchElasticContent(elastic).testTag("search_results"),
            state = listState,
            overscrollEffect = null,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(state.results, key = SearchResultCity::cityId) { city ->
                val added = city.cityId in state.addedKeys
                SearchResultRow(city, state.query.trim(), added) { if (added) onAlreadyAdded() else onCityClick(city) }
            }
        }
    }
}

@Composable
private fun SearchResultRow(city: SearchResultCity, query: String, added: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectWeatherPressedAsState()
    val color = colorResource(when { added -> R.color.color_city_added; pressed -> R.color.app_on_accent_color; else -> R.color.app_primary_text_color })
    val highlight = colorResource(R.color.highlight_text_color)
    Row(
        Modifier.fillMaxWidth().height(60.dp).testTag("search_city_${city.cityId}").weatherDrawableBackground(R.drawable.selector_listitem, pressed = pressed)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 18.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(highlightSearch(city.county, query, highlight), 18.dp, color, maxLines = 1)
        if (city.searchContext.isNotBlank()) {
            PixelText(stringResource(R.string.separator), 18.dp, color, maxLines = 1)
            PixelText(highlightSearch(city.searchContext, query, highlight), 18.dp, color, Modifier.weight(1f), maxLines = 1)
        } else Spacer(Modifier.weight(1f))
        if (added) PixelText(stringResource(R.string.added_city), 18.dp, colorResource(R.color.color_city_added), Modifier.padding(start = 0.67.dp), maxLines = 1)
    }
}

internal fun highlightSearch(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isNotEmpty()) {
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            addStyle(SpanStyle(color = color), index, index + query.length)
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
    }
}

@Composable
private fun SearchMessage(error: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(stringResource(if (error) R.string.weather_search_no_connect_info else R.string.weather_search_empty_info1),
            19.dp, colorResource(R.color.search_no_result_text_color1), fontWeight = FontWeight.Bold)
        PixelText(stringResource(if (error) R.string.weather_search_no_connect_info_tip else R.string.weather_search_empty_info2),
            15.dp, colorResource(R.color.search_no_result_text_color2), Modifier.padding(top = 3.dp))
        if (error) OriginalTextButton(stringResource(R.string.weather_search_refresh), R.drawable.selector_small_btn_standard,
            Modifier.padding(top = 3.dp).size(120.dp, 48.dp), onClick = onRetry)
    }
}

@Composable
private fun SearchLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "search loading")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "spinner angle")
    WeatherDrawable(R.drawable.spinner_48_outer_smartisanos_light, null, modifier.size(48.dp).rotate(angle))
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun SearchCityPreview() {
    SearchCityScreen(SearchUiState(), onQueryChange = {}, onCancel = {}, onRetry = {}, onCityClick = {}, onHotCityClick = {}, onLocationClick = {}, onAlreadyAdded = {}, requestKeyboard = false)
}
