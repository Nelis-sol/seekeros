package com.myagentos.app

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color

/**
 * Simple Markdown formatter for Android TextView
 * Supports: **bold**, *italic*, ###headings, `code`, etc.
 */
object MarkdownFormatter {
    
    fun format(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        
        // Process in order: headings, code, bold, italic
        processHeadings(builder)
        processCode(builder)
        processBold(builder)
        processItalic(builder)
        
        return builder
    }
    
    private fun processBold(builder: SpannableStringBuilder) {
        // Match **text** pattern
        val pattern = Regex("""\*\*([^*]+)\*\*""")
        var offset = 0
        
        pattern.findAll(builder.toString()).forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            // Replace **text** with text (remove the ** markers)
            builder.replace(start, end, content)
            
            // Apply bold style
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Adjust offset for removed characters (4 characters: 2 on each side)
            offset += 4
        }
    }
    
    private fun processItalic(builder: SpannableStringBuilder) {
        // Match *text* pattern (but not ** which is bold)
        val pattern = Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")
        var offset = 0
        
        pattern.findAll(builder.toString()).forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            // Replace *text* with text
            builder.replace(start, end, content)
            
            // Apply italic style
            builder.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Adjust offset for removed characters (2 characters)
            offset += 2
        }
    }
    
    private fun processHeadings(builder: SpannableStringBuilder) {
        // Match ###heading or ## heading or # heading at start of line
        val pattern = Regex("""^(#{1,6})\s+(.+)$""", RegexOption.MULTILINE)
        var offset = 0
        
        pattern.findAll(builder.toString()).forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val hashes = match.groupValues[1]
            val content = match.groupValues[2]
            val level = hashes.length
            
            // Replace ###heading with heading
            builder.replace(start, end, content)
            
            // Apply heading style based on level
            val sizeMultiplier = when (level) {
                1 -> 1.8f
                2 -> 1.5f
                3 -> 1.3f
                else -> 1.2f
            }
            
            builder.setSpan(
                RelativeSizeSpan(sizeMultiplier),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Adjust offset for removed characters
            offset += (hashes.length + 1) // +1 for the space
        }
    }
    
    private fun processCode(builder: SpannableStringBuilder) {
        // Match `code` pattern
        val pattern = Regex("""`([^`]+)`""")
        var offset = 0
        
        pattern.findAll(builder.toString()).forEach { match ->
            val start = match.range.first - offset
            val end = match.range.last + 1 - offset
            val content = match.groupValues[1]
            
            // Replace `code` with code
            builder.replace(start, end, content)
            
            // Apply code style (monospace + gray color)
            builder.setSpan(
                android.text.style.TypefaceSpan("monospace"),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor("#A0A0A0")),
                start,
                start + content.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Adjust offset for removed characters (2 backticks)
            offset += 2
        }
    }
}

