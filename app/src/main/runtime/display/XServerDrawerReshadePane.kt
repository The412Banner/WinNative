package com.winlator.cmod.runtime.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.runtime.reshade.ReshadeManager
import kotlin.math.roundToInt

// In-game ReShade pane. Mirrors ScreenEffectsPaneContent's layout/idiom but drives the live vkBasalt
// config rewrite through the ReShade listener callbacks: a master on/off toggle, a live effect switcher,
// and typed controls for the running effect's reflected uniforms (same control types + key scheme as the
// pre-launch ReshadeSection in GameSettings.kt). The host rewrites vkBasalt.conf and the patched
// libvkbasalt.so applies it on the next rendered frame.

@Composable
internal fun ReshadePaneContent(
    state: XServerDrawerState,
    listener: XServerDrawerActionListener,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneScale = computePaneScale(maxHeight)
        CompositionLocalProvider(LocalPaneScale provides paneScale) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = (12f * paneScale).dp, vertical = (12f * paneScale).dp),
                verticalArrangement = Arrangement.spacedBy((10f * paneScale).dp),
            ) {
                NavBooleanRow(
                    title = stringResource(R.string.reshade_drawer_enable),
                    checked = state.reshadeEnabled,
                    onCheckedChange = listener::onReshadeEnabledChanged,
                )

                if (state.reshadeEffectNames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                        PaneSectionLabel(stringResource(R.string.reshade_drawer_effect))
                        InputControlsSimpleDropdown(
                            options = state.reshadeEffectNames,
                            selectedIndex = state.reshadeSelectedIndex,
                            onSelected = listener::onReshadeEffectSelected,
                        )
                    }
                }

                if (state.reshadeEnabled && state.reshadeSelectedIndex >= 1) {
                    ThinDivider()

                    Column(verticalArrangement = Arrangement.spacedBy((8f * paneScale).dp)) {
                        PaneSectionLabel(stringResource(R.string.reshade_params_title))

                        if (state.reshadeParamDefs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reshade_drawer_no_params),
                                color = DrawerTextSecondary,
                                fontSize = (13f * paneScale).sp,
                            )
                        } else {
                            for (p in state.reshadeParamDefs) {
                                ReshadeParamRow(state = state, param = p, listener = listener)
                            }

                            Box(
                                Modifier.fillMaxWidth().paneNavItem(
                                    cornerRadius = (12f * paneScale).dp,
                                    onActivate = { listener.onReshadeReset() },
                                ),
                            ) {
                                DrawerResetRow(
                                    label = stringResource(R.string.reshade_params_reset),
                                    onClick = listener::onReshadeReset,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// One reflected uniform, rendered with the control type matching its ParamType (parallels the pre-launch
// ReshadeParamControls). Every edit routes through onReshadeParamChanged(key, value) in the shared
// seedValues key scheme so the host's live conf rewrite and the pre-launch save agree.
@Composable
private fun ReshadeParamRow(
    state: XServerDrawerState,
    param: ReshadeManager.ReshadeParam,
    listener: XServerDrawerActionListener,
) {
    when (param.type) {
        ReshadeManager.ParamType.BOOL -> {
            val v = state.reshadeParamValues[param.name] ?: param.defaultValue
            NavBooleanRow(
                title = param.label,
                checked = v != 0f,
                onCheckedChange = { listener.onReshadeParamChanged(param.name, if (it) 1f else 0f) },
            )
        }
        ReshadeManager.ParamType.COMBO -> {
            val options = param.options ?: emptyList()
            val v = (state.reshadeParamValues[param.name] ?: param.defaultValue).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy((6f * LocalPaneScale.current).dp)) {
                PaneSectionLabel(param.label)
                InputControlsSimpleDropdown(
                    options = options,
                    selectedIndex = v.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
                    onSelected = { listener.onReshadeParamChanged(param.name, it.toFloat()) },
                )
            }
        }
        ReshadeManager.ParamType.COLOR -> ReshadeColorRow(state, param, listener)
        ReshadeManager.ParamType.INT ->
            ReshadeSliderRow(
                label = param.label,
                value = state.reshadeParamValues[param.name] ?: param.defaultValue,
                min = param.min,
                max = param.max,
                step = if (param.step > 0f) param.step else 1f,
                whole = true,
                onValueChange = { listener.onReshadeParamChanged(param.name, it) },
            )
        else -> // FLOAT
            ReshadeSliderRow(
                label = param.label,
                value = state.reshadeParamValues[param.name] ?: param.defaultValue,
                min = param.min,
                max = param.max,
                step = param.step,
                whole = false,
                onValueChange = { listener.onReshadeParamChanged(param.name, it) },
            )
    }
}

// Color param: a tappable swatch that expands to per-channel (R/G/B[/A]) sliders — same affordance as
// the pre-launch ReshadeColorControl, trimmed to the drawer's components.
@Composable
private fun ReshadeColorRow(
    state: XServerDrawerState,
    param: ReshadeManager.ReshadeParam,
    listener: XServerDrawerActionListener,
) {
    val paneScale = LocalPaneScale.current
    var expanded by remember(param.name) { mutableStateOf(false) }

    fun comp(c: Int): Float =
        state.reshadeParamValues[param.name + "_" + c] ?: (param.componentDefaults?.getOrNull(c) ?: 0f)
    val r = comp(0)
    val g = if (param.components > 1) comp(1) else r
    val b = if (param.components > 2) comp(2) else r
    val swatch = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((10f * paneScale).dp))
                .paneNavItem(
                    cornerRadius = (10f * paneScale).dp,
                    onActivate = { expanded = !expanded },
                )
                .clickable { expanded = !expanded }
                .padding(vertical = (4f * paneScale).dp),
    ) {
        Box(
            Modifier
                .size((22f * paneScale).dp)
                .clip(RoundedCornerShape((5f * paneScale).dp))
                .background(swatch)
                .border(1.dp, RestingCardBorder, RoundedCornerShape((5f * paneScale).dp)),
        )
        Spacer(Modifier.width((10f * paneScale).dp))
        Text(
            text = param.label,
            color = DrawerTextPrimary,
            fontSize = (14f * paneScale).sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = DrawerTextSecondary,
        )
    }
    if (expanded) {
        for (c in 0 until param.components) {
            val key = param.name + "_" + c
            ReshadeSliderRow(
                label = reshadeColorComponentLabel(c, param.components),
                value = state.reshadeParamValues[key] ?: (param.componentDefaults?.getOrNull(c) ?: 0f),
                min = 0f,
                max = 1f,
                step = 0.01f,
                whole = false,
                onValueChange = { listener.onReshadeParamChanged(key, it) },
            )
        }
    }
}

private fun reshadeColorComponentLabel(c: Int, components: Int): String = when {
    components >= 4 && c == 3 -> "A"
    c == 0 -> "R"
    c == 1 -> "G"
    c == 2 -> "B"
    else -> (c + 1).toString()
}

// Float/int slider on top of the shared NavSliderRow — snaps to [step] (or integers when [whole]) so the
// value written into vkBasalt.conf is clean, and steps the d-pad by one increment.
@Composable
private fun ReshadeSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    whole: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val lo = min
    val hi = if (max > min) max else min + 1f
    val inc = if (whole) 1f else (if (step > 0f) step else (hi - lo) / 100f)
    val valueText =
        if (whole) value.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.2f", value)

    fun snap(raw: Float): Float {
        val v =
            when {
                whole -> raw.roundToInt().toFloat()
                step > 0f -> lo + Math.round((raw - lo) / step) * step
                else -> raw
            }
        return v.coerceIn(lo, hi)
    }

    NavSliderRow(
        label = label,
        valueText = valueText,
        value = value.coerceIn(lo, hi),
        valueRange = lo..hi,
        steps = 0,
        onValueChange = { onValueChange(snap(it)) },
        adjustStep = inc,
    )
}
