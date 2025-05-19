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
        val spacing = 4.5.dp.toPx() // Balanced spacing between lines
        val centerY = size.height / 2
        
        // Calculate line heights
        val maxHeight = size.height * 0.7f // 70% of total height
        val heights = listOf(
            maxHeight * 0.33f,  // First line: 1/3 of max height
            maxHeight,          // Second line: full height
            maxHeight * 0.5f,   // Third line: half height
            maxHeight * 0.33f   // Fourth line: 1/3 of max height
        )
        
        // Calculate total width of the icon
        val totalWidth = (heights.size - 1) * spacing
        
        // Calculate starting x position to center the entire icon
        val startX = (size.width - totalWidth) / 2
        
        // Draw each line
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