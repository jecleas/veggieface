@file:OptIn(ExperimentalTextApi::class)

package com.lexi.veggieface

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VeggieFaceApp() }
    }
}

// MARK: - Look & feel

private val Ink = hex(0x4A3528)
private val Cream = hex(0xFFF4E8)
private val Pink = hex(0xFF7EB0)
private val Green = hex(0x5CC46B)
private val Orange = hex(0xFF9F43)
private val Purple = hex(0xA77BFF)

val Nunito = FontFamily(
    listOf(600, 700, 800, 900).map { w -> Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

@Composable
fun Txt(text: String, size: Int, weight: FontWeight = FontWeight.Bold, color: Color = Ink, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, color = color, textAlign = align, lineHeight = (size * 1.2f).sp))
}

@Composable
fun Modifier.squish(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "squish")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, fontSize: Int = 22, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(modifier.squish(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).background(color.copy(alpha = 0.55f), shape))
        Txt(
            text, fontSize, FontWeight.Black, Color.White, TextAlign.Center,
            Modifier.fillMaxWidth().background(color, shape).border(3.dp, Color.White, shape).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Txt(
        text, 15, FontWeight.ExtraBold, if (selected) Color.White else Ink,
        modifier = Modifier.squish(onClick = onClick).background(if (selected) Pink else Color.White, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// MARK: - The picture

/** How the kid moved the face: shift (fraction of picture width), size and twist. */
data class FaceFit(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f, val rotation: Float = 0f)

fun backgroundFor(p: Produce) = Brush.verticalGradient(listOf(Color.White, lerp(Color.White, p.tint, 0.25f)))

fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Draws the veggie, then the face on top. Used on screen AND for the saved picture. */
fun DrawScope.drawVeggieFace(p: Produce, face: ImageBitmap?, fit: FaceFit, tint: Float, blush: Boolean) {
    val w = size.minDimension
    drawProduce(p, w)
    if (face == null) return
    val targetW = p.faceSize * w * fit.scale
    val k = targetW / face.width
    val h = face.height * k
    val c = Offset((p.faceX + fit.dx) * w, (p.faceY + fit.dy) * w)
    rotate(fit.rotation, c) {
        withTransform({
            translate(c.x - targetW / 2, c.y - h / 2)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(face)
            // "Veggie color": paint the face the veggie's color (only where the face is).
            if (tint > 0f) drawImage(face, alpha = tint, colorFilter = ColorFilter.tint(p.tint, BlendMode.Modulate))
        }
        if (blush) for (side in listOf(-1f, 1f)) {
            val bw = targetW * 0.2f
            drawOval(hex(0xFF6F91).copy(alpha = 0.45f), Offset(c.x + side * targetW * 0.27f - bw / 2, c.y + h * 0.08f), Size(bw, bw * 0.55f))
        }
    }
}

/** Makes a 1080 × 1080 picture to save or share. */
fun renderPicture(p: Produce, face: ImageBitmap?, fit: FaceFit, tint: Float, blush: Boolean): Bitmap {
    val img = ImageBitmap(1080, 1080)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(1080f, 1080f)) {
        drawRect(backgroundFor(p))
        drawVeggieFace(p, face, fit, tint, blush)
    }
    return img.asAndroidBitmap()
}

// MARK: - App

@Composable
fun VeggieFaceApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var face by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var produce by remember { mutableStateOf(Produce.Tomato) }
    var fit by remember { mutableStateOf(FaceFit()) }
    var tint by remember { mutableFloatStateOf(0f) }
    var blush by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        scope.launch {
            busy = true
            val photo = FaceCutter.load(context, uri)
            if (photo == null) {
                message = "Couldn't open that photo 😕"
            } else {
                val cut = FaceCutter.cut(photo)
                face = cut.bitmap.asImageBitmap()
                fit = FaceFit()
                message = if (cut.found) "Found a face! 🎉 Pick a veggie." else "Couldn't find a face, so I used the middle. Drag it into place!"
            }
            busy = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::process)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::process)
    }
    val openCamera = {
        val uri = FaceCutter.newPhotoUri(context)
        cameraUri = uri
        takePicture.launch(uri)
    }
    val openGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(message) {
        if (message != null) { delay(3000); message = null }
    }

    Box(Modifier.fillMaxSize().background(Cream)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt("Veggie Face", 36, FontWeight.Black, Pink)
            if (face == null) {
                StartScreen(openCamera, openGallery)
            } else {
                Editor(
                    produce, face, fit, tint, blush,
                    onProduce = { produce = it; fit = FaceFit() },
                    onFit = { fit = it },
                    onTint = { tint = it },
                    onBlush = { blush = it },
                    onSave = {
                        scope.launch {
                            val ok = FaceCutter.saveToGallery(context, renderPicture(produce, face, fit, tint, blush))
                            message = if (ok) "Saved to your gallery! 📸" else "Couldn't save 😕"
                        }
                    },
                    onShare = { scope.launch { FaceCutter.share(context, renderPicture(produce, face, fit, tint, blush)) } },
                    onCamera = openCamera,
                    onGallery = openGallery,
                )
            }
        }

        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
                Txt("Finding the face… 🔍", 24, FontWeight.Black, Ink, modifier = Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp))
            }
        }
        message?.let {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 70.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.TopCenter) {
                Txt(it, 17, color = Color.White, align = TextAlign.Center, modifier = Modifier.background(Ink.copy(alpha = 0.9f), RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun StartScreen(openCamera: () -> Unit, openGallery: () -> Unit) {
    Txt("Put anyone's face on a fruit or veggie!", 20, FontWeight.ExtraBold, align = TextAlign.Center)
    val t = rememberInfiniteTransition(label = "wiggle")
    val wiggle by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in Produce.entries.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, p) in row.withIndex()) {
                    Canvas(Modifier.size(76.dp).graphicsLayer { rotationZ = if (i % 2 == 0) wiggle else -wiggle }) { drawProduce(p, size.width) }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    BigButton("📸  Take a photo", Pink, Modifier.fillMaxWidth(), 24, openCamera)
    BigButton("🖼️  Pick a photo", Purple, Modifier.fillMaxWidth(), 24, openGallery)
    Txt("Tip: a big, front-facing face works best!", 15, FontWeight.SemiBold, Ink.copy(alpha = 0.6f))
}

@Composable
private fun Editor(
    produce: Produce, face: ImageBitmap?, fit: FaceFit, tint: Float, blush: Boolean,
    onProduce: (Produce) -> Unit, onFit: (FaceFit) -> Unit, onTint: (Float) -> Unit, onBlush: (Boolean) -> Unit,
    onSave: () -> Unit, onShare: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit,
) {
    var current by remember { mutableStateOf(fit) }
    current = fit
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).shadow(8.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
            .background(backgroundFor(produce))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val w = size.width.toFloat()
                    current = current.copy(
                        dx = current.dx + pan.x / w,
                        dy = current.dy + pan.y / w,
                        scale = (current.scale * zoom).coerceIn(0.3f, 4f),
                        rotation = current.rotation + rotation,
                    )
                    onFit(current)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawVeggieFace(produce, face, fit, tint, blush) }
    }
    Txt("Drag, pinch and twist with two fingers to fit the face", 14, FontWeight.SemiBold, Ink.copy(alpha = 0.6f), TextAlign.Center)

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in Produce.entries) {
            Column(
                Modifier.squish { onProduce(p) }
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .border(3.dp, if (p == produce) Pink else Color.Transparent, RoundedCornerShape(18.dp))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(58.dp)) { drawProduce(p, size.width) }
                Txt(p.title, 12, FontWeight.ExtraBold)
            }
        }
    }

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Veggie color:", 15, FontWeight.ExtraBold)
        Chip("Off", tint == 0f) { onTint(0f) }
        Chip("A bit", tint == 0.35f) { onTint(0.35f) }
        Chip("Lots", tint == 0.7f) { onTint(0.7f) }
        Chip("😊 Blush", blush) { onBlush(!blush) }
        Chip("↺ Reset", false) { onFit(FaceFit()) }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("💾 Save", Green, Modifier.weight(1f), onClick = onSave)
        BigButton("📤 Share", Orange, Modifier.weight(1f), onClick = onShare)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("📸 New photo", Pink, Modifier.weight(1f), 18, onCamera)
        BigButton("🖼️ Gallery", Purple, Modifier.weight(1f), 18, onGallery)
    }
}
