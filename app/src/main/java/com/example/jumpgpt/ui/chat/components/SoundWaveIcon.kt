package com.example.jumpgpt.ui.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun SoundWaveIcon(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(
        modifier = modifier.size(24.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        val spacing = 4.5.dp.toPx()
        val centerY = size.height / 2
        
        val maxHeight = size.height * 0.7f
        val heights = listOf(
            maxHeight * 0.33f,
            maxHeight,
            maxHeight * 0.5f,
            maxHeight * 0.33f
        )
        
        val totalWidth = (heights.size - 1) * spacing
        
        val startX = (size.width - totalWidth) / 2
        
        heights.forEachIndexed { index, height ->
            val x = startX + (index * spacing)
            val startY = centerY - (height / 2)
            val endY = centerY + (height / 2)
            
            drawLine(
                color = color,
                start = Offset(x, startY),
                end = Offset(x, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
} 