package com.dc.aurora.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dc.aurora.ui.theme.*

@Composable
fun AuroraScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 입력창은 ViewModel이 관리 (공유 텍스트 prefill 지원)
    val inputText = state.inputText

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        AuroraGlowBackground()

        if (state.messages.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 100.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 40.dp, bottom = 16.dp),
            ) {
                items(state.messages) { msg -> MessageBubble(msg) }
                if (state.isLoading) { item { TypingIndicator() } }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.messages.isEmpty()) Modifier.align(Alignment.Center)
                    else Modifier.align(Alignment.BottomCenter)
                )
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.messages.isEmpty()) {
                    Text(
                        text = "일정 문자를 공유하거나 직접 말씀해 주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                AuroraChatInput(
                    value = inputText,
                    onValueChange = { vm.onInputChange(it) },
                    onSend = { vm.send() },
                    isLoading = state.isLoading,
                )
            }
        }
    }
}

// ── 오로라 배경 글로우 ─────────────────────────────────────────────────────────

@Composable
private fun AuroraGlowBackground() {
    val infinite = rememberInfiniteTransition(label = "aurora")

    val x1 by infinite.animateFloat(
        initialValue = 0.2f, targetValue = 0.7f, label = "x1",
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
    )
    val y1 by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f, label = "y1",
        animationSpec = infiniteRepeatable(tween(9000, easing = EaseInOutSine), RepeatMode.Reverse),
    )
    val x2 by infinite.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f, label = "x2",
        animationSpec = infiniteRepeatable(tween(8000, easing = EaseInOutSine), RepeatMode.Reverse),
    )
    val y2 by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 0.8f, label = "y2",
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
    )
    val x3 by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 0.2f, label = "x3",
        animationSpec = infiniteRepeatable(tween(10000, easing = EaseInOutSine), RepeatMode.Reverse),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        Box(
            modifier = Modifier
                .offset(x = w * x1 - 140.dp, y = h * y1 - 140.dp)
                .size(280.dp)
                .blur(90.dp)
                .background(AuroraIndigo.copy(alpha = 0.45f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = w * x2 - 120.dp, y = h * y2 - 120.dp)
                .size(240.dp)
                .blur(80.dp)
                .background(AuroraViolet.copy(alpha = 0.4f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = w * x3 - 100.dp, y = h * 0.6f - 100.dp)
                .size(200.dp)
                .blur(70.dp)
                .background(AuroraTeal.copy(alpha = 0.3f), CircleShape)
        )
    }
}

// ── 채팅 입력창 ──────────────────────────────────────────────────────────────

@Composable
private fun AuroraChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    val glow by rememberInfiniteTransition(label = "inputGlow").animateFloat(
        initialValue = 0.6f, targetValue = 1f, label = "glow",
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(listOf(
                    AuroraIndigo.copy(alpha = 0.25f * glow),
                    AuroraViolet.copy(alpha = 0.25f * glow),
                    AuroraTeal.copy(alpha = 0.2f * glow),
                ))
            )
            .background(SurfaceHigh.copy(alpha = 0.85f), RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text("메시지 입력...", color = Muted, style = MaterialTheme.typography.bodyLarge)
                },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground,
                    cursorColor = AuroraViolet,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (value.isNotBlank() && !isLoading)
                            Brush.linearGradient(listOf(AuroraIndigo, AuroraViolet))
                        else Brush.linearGradient(listOf(SurfaceHigh, SurfaceHigh))
                    )
                    .size(44.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "전송",
                    tint = if (value.isNotBlank() && !isLoading) OnBackground else Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── 메시지 버블 ───────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (msg.isUser) 20.dp else 4.dp,
                        bottomEnd = if (msg.isUser) 4.dp else 20.dp,
                    )
                )
                .background(if (msg.isUser) UserBubble else AiBubble)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = parseMiniMarkdown(msg.text),
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.isUser) UserText else AiText,
            )
        }
    }
}

/**
 * **bold** 구문을 SpanStyle(fontWeight=Bold)로 변환.
 * AI 응답의 일정 제목·강조 표시에 사용.
 */
@Composable
private fun parseMiniMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val bold = androidx.compose.ui.text.font.FontWeight.Bold
        var cursor = 0
        val regex = Regex("""\*\*(.+?)\*\*""")
        for (match in regex.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = bold))
            append(match.groupValues[1])
            pop()
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

// ── 타이핑 인디케이터 ─────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    val infinite = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp))
            .background(AiBubble)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        repeat(3) { i ->
            val alpha by infinite.animateFloat(
                initialValue = 0.3f, targetValue = 1f, label = "dot$i",
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 150, easing = EaseInOutSine),
                    RepeatMode.Reverse,
                ),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AuroraViolet.copy(alpha = alpha))
            )
        }
    }
}
