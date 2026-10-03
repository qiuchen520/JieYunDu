// 文件：TopGlassNavBar.kt
// 职责：Q 弹玻璃导航条核心——支持竖直/横向双方向，含可拖拽指示器与 spring 物理
// 依赖：Compose foundation/animation/ui、JieYunDuColors、Dimens、MaterialTheme
// 协议：AGPL-3.0

package com.jieyundu.app.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 玻璃导航条方向。 */
enum class GlassBarOrientation {
    /** 竖直（平板左侧，宽 96dp）。 */
    Vertical,

    /** 横向（手机顶部，高 64dp）。 */
    Horizontal
}

/** 松手吸附弹簧（10.3：dampingRatio 0.55 / stiffness 380）。 */
private val SnapSpring: SpringSpec<Float> = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)

/** 点击切换弹簧（10.3：0.62 / 420）。 */
private val ClickSpring: SpringSpec<Float> = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)

/** 尺寸 / 抓取弹簧（10.3：0.7 / 500）。 */
private val SizeSpring: SpringSpec<Float> = spring<Float>(dampingRatio = 0.7f, stiffness = 500f)

/** 按下缩放弹簧（10.3：0.4 / 800）。 */
private val PressSpring: SpringSpec<Float> = spring<Float>(dampingRatio = 0.4f, stiffness = 800f)

/** 按下缩放目标值（10.3：0.94）。 */
private const val PRESSED_SCALE = 0.94f

/** 拖拽抓取缩放目标值（10.4：1.04）。 */
private const val GRAB_SCALE = 1.04f

/** 常态缩放值。 */
private const val REST_SCALE = 1f

/**
 * Q 弹玻璃导航条（第十部分核心，支持双方向）。
 *
 * 说明：指示器由 [Animatable] 驱动，仅使用 spring（R6/D4）；拖拽时零动画跟手，
 * 松手用 [SnapSpring] 吸附最近页签，点击用 [ClickSpring]。（10.4 / 10.5）
 *
 * @param labels 页签文案（来自 strings.xml，禁止硬编码）。
 * @param selectedIndex 当前选中下标。
 * @param onSelect 选中回调（点击或松手吸附后触发）。
 * @param orientation 导航条方向（竖直/横向）。
 * @param modifier 外部修饰符（提供主轴向尺寸：横向填宽、竖向填高）。
 */
@Composable
fun TopGlassNavBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    orientation: GlassBarOrientation,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isHorizontal = orientation == GlassBarOrientation.Horizontal
    val thickness = if (isHorizontal) Dimens.NavBarThicknessHorizontal else Dimens.NavBarThicknessVertical
    val shape = RoundedCornerShape(thickness / 2)
    val tabCount = labels.size.coerceAtLeast(1)

    val indicatorOffset = remember { Animatable(0f) }
    val indicatorScale = remember { Animatable(REST_SCALE) }
    val isDragging = remember { mutableStateOf(false) }
    val pressedIndex = remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    var dragBase by remember { mutableFloatStateOf(0f) }

    val sizedModifier = if (isHorizontal) modifier.height(thickness) else modifier.width(thickness)

    BoxWithConstraints(
        modifier = sizedModifier
            .clip(shape)
            .background(color = JieYunDuColors.GlassFill, shape = shape)
            .border(width = Dimens.HighlightStroke, color = JieYunDuColors.GlassBorder, shape = shape)
    ) {
        val mainAxisPx = (if (isHorizontal) constraints.maxWidth else constraints.maxHeight).toFloat()
        val slotPx = mainAxisPx / tabCount
        val maxOffsetPx = (mainAxisPx - slotPx).coerceAtLeast(0f)
        val insetPx = with(density) { Dimens.NavIndicatorInset.toPx() }
        val highlightPx = with(density) { Dimens.NavIndicatorStroke.toPx() }
        val indicatorShortSide = thickness - Dimens.NavIndicatorInset * 2
        val indicatorShape = RoundedCornerShape(indicatorShortSide / 2)

        /** 依据主轴坐标换算最近的页签下标。 */
        val indexAt: (Float) -> Int = { axis ->
            val safeSlot = if (slotPx > 0f) slotPx else 1f
            (axis / safeSlot).toInt().coerceIn(0, tabCount - 1)
        }

        LaunchedEffect(selectedIndex, slotPx) {
            if (slotPx > 0f && !isDragging.value) {
                indicatorOffset.animateTo((slotPx * selectedIndex).coerceIn(0f, maxOffsetPx), ClickSpring)
            }
        }

        // 指示器
        val indicatorModifier = if (isHorizontal) {
            Modifier
                .offset { IntOffset(indicatorOffset.value.roundToInt(), insetPx.roundToInt()) }
                .size(width = with(density) { slotPx.toDp() }, height = indicatorShortSide)
        } else {
            Modifier
                .offset { IntOffset(insetPx.roundToInt(), indicatorOffset.value.roundToInt()) }
                .size(width = indicatorShortSide, height = with(density) { slotPx.toDp() })
        }
        Box(
            modifier = indicatorModifier
                .graphicsLayer {
                    scaleX = indicatorScale.value
                    scaleY = indicatorScale.value
                }
                .clip(indicatorShape)
                .background(color = JieYunDuColors.NavIndicatorFill, shape = indicatorShape)
                .drawWithContent {
                    drawContent()
                    drawIndicatorHighlight(isHorizontal, highlightPx)
                }
        )

        // 页签文字
        if (isHorizontal) {
            Row(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        NavLabel(text = label, selected = index == selectedIndex, pressed = index == pressedIndex.intValue)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        NavLabel(text = label, selected = index == selectedIndex, pressed = index == pressedIndex.intValue)
                    }
                }
            }
        }

        // 手势层（透明，置顶）：点击 → 切换；拖拽 → 跟手 + 松手吸附
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabCount, slotPx) {
                    detectTapGestures(
                        onPress = { position ->
                            val axis = if (isHorizontal) position.x else position.y
                            pressedIndex.intValue = indexAt(axis)
                            tryAwaitRelease()
                            pressedIndex.intValue = -1
                        },
                        onTap = { position ->
                            val axis = if (isHorizontal) position.x else position.y
                            onSelect(indexAt(axis))
                        }
                    )
                }
                .pointerInput(tabCount, slotPx, maxOffsetPx, isHorizontal) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging.value = true
                            scope.launch {
                                indicatorScale.stop()
                                indicatorScale.animateTo(GRAB_SCALE, SizeSpring)
                            }
                            dragBase = indicatorOffset.value
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = if (isHorizontal) dragAmount.x else dragAmount.y
                            dragBase = (dragBase + delta).coerceIn(0f, maxOffsetPx)
                            scope.launch { indicatorOffset.snapTo(dragBase) }
                        },
                        onDragEnd = {
                            val nearest = indexAt(indicatorOffset.value + slotPx / 2f)
                            scope.launch {
                                indicatorOffset.animateTo((slotPx * nearest).coerceIn(0f, maxOffsetPx), SnapSpring)
                                indicatorScale.animateTo(REST_SCALE, SizeSpring)
                                isDragging.value = false
                            }
                            onSelect(nearest)
                        },
                        onDragCancel = {
                            val nearest = indexAt(indicatorOffset.value + slotPx / 2f)
                            scope.launch {
                                indicatorOffset.animateTo((slotPx * nearest).coerceIn(0f, maxOffsetPx), SnapSpring)
                                indicatorScale.animateTo(REST_SCALE, SizeSpring)
                                isDragging.value = false
                            }
                        }
                    )
                }
        )
    }
}

/**
 * 单个页签文字（含按下缩放反馈，10.3：0.94）。
 *
 * @param text 页签文案。
 * @param selected 是否选中。
 * @param pressed 是否被按下。
 */
@Composable
private fun NavLabel(text: String, selected: Boolean, pressed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else REST_SCALE,
        animationSpec = PressSpring,
        label = "navLabelPress"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (selected) JieYunDuColors.TextPrimary else JieYunDuColors.TextTertiary,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

/**
 * 沿指示器对应边缘绘制高光渐变线（10.2：横向取上边缘，竖向取左边缘）。
 *
 * @param isHorizontal 是否横向导航条。
 * @param strokePx 线宽（像素）。
 */
private fun DrawScope.drawIndicatorHighlight(isHorizontal: Boolean, strokePx: Float) {
    if (isHorizontal) {
        val brush = Brush.horizontalGradient(
            colors = listOf(JieYunDuColors.NavIndicatorHighlight, JieYunDuColors.GlassHighlightClear)
        )
        drawLine(
            brush = brush,
            start = Offset(0f, strokePx / 2f),
            end = Offset(size.width, strokePx / 2f),
            strokeWidth = strokePx
        )
    } else {
        val brush = Brush.verticalGradient(
            colors = listOf(JieYunDuColors.NavIndicatorHighlight, JieYunDuColors.GlassHighlightClear)
        )
        drawLine(
            brush = brush,
            start = Offset(strokePx / 2f, 0f),
            end = Offset(strokePx / 2f, size.height),
            strokeWidth = strokePx
        )
    }
}