package com.example.genelek2

import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.widget.TextView
import androidx.annotation.ColorInt
import java.io.ByteArrayOutputStream



object TextUtil {


    @ColorInt
    var caretBackground: Int = 0xff666666.toInt()

    const val newline_crlf = "\r\n"
    const val newline_lf = "\n"

    fun fromHexString(s: CharSequence): ByteArray {
        val buf = ByteArrayOutputStream()
        var b: Byte = 0
        var nibble = 0
        for (pos in s.indices) {
            if (nibble == 2) {
                buf.write(b.toInt())
                nibble = 0
                b = 0
            }
            val c = s[pos].code
            when {
                c in '0'.code..'9'.code -> {
                    nibble++
                    b = (b * 16 + (c - '0'.code)).toByte()
                }
                c in 'A'.code..'F'.code -> {
                    nibble++
                    b = (b * 16 + (c - 'A'.code + 10)).toByte()
                }
                c in 'a'.code..'f'.code -> {
                    nibble++
                    b = (b * 16 + (c - 'a'.code + 10)).toByte()
                }
            }
        }
        if (nibble > 0) {
            buf.write(b.toInt())
        }
        return buf.toByteArray()
    }

    //returns the hexadecimal string representation of byte array
    fun toHexString(buf: ByteArray): String {
        return toHexString(buf, 0, buf.size)
    }

    //creates a StringBuilder to hold the resulting hexadecimal string
    fun toHexString(buf: ByteArray, begin: Int, end: Int): String {
        val sb = StringBuilder(3 * (end - begin))
        toHexString(sb, buf, begin, end)
        return sb.toString()
    }

    fun toHexString(sb: StringBuilder, buf: ByteArray) {
        toHexString(sb, buf, 0, buf.size)
    }

    //conversion from bytes to hex
    fun toHexString(sb: StringBuilder, buf: ByteArray, begin: Int, end: Int) {
        for (pos in begin until end) {
            if (sb.isNotEmpty()) sb.append(' ')
            var c: Int
            c = (buf[pos].toInt() and 0xff) / 16
            sb.append(if (c >= 10) (c + 'A'.code - 10).toChar() else (c + '0'.code).toChar())
            c = (buf[pos].toInt() and 0xff) % 16
            sb.append(if (c >= 10) (c + 'A'.code - 10).toChar() else (c + '0'.code).toChar())
        }
    }


    fun toCaretString(s: CharSequence, keepNewline: Boolean): CharSequence {
        return toCaretString(s, keepNewline, s.length)
    }

    fun toCaretString(s: CharSequence, keepNewline: Boolean, length: Int): CharSequence {
        var found = false
        for (pos in 0 until length) {
            if (s[pos] < 32.toChar() && (!keepNewline || s[pos] != '\n')) {
                found = true
                break
            }
        }
        if (!found) return s

        val sb = SpannableStringBuilder()
        for (pos in 0 until length) {
            if (s[pos] < 32.toChar() && (!keepNewline || s[pos] != '\n')) {
                sb.append('^')
                sb.append((s[pos].code + 64).toChar())
                sb.setSpan(
                    BackgroundColorSpan(caretBackground),
                    sb.length - 2,
                    sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                sb.append(s[pos])
            }
        }
        return sb
    }

    class HexWatcher(private val view: TextView) : TextWatcher {

        private val sb = StringBuilder()
        private var self = false
        private var enabled = false

        fun enable(enable: Boolean) {
            if (enable) {
                view.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            enabled = enable
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (!enabled || self) return

            sb.clear()
            s?.forEach { c ->
                when {
                    c in '0'..'9' -> sb.append(c)
                    c in 'A'..'F' -> sb.append(c)
                    c in 'a'..'f' -> sb.append((c.code + 'A'.code - 'a'.code).toChar())
                }
            }
            for (i in 2 until sb.length step 3) {
                sb.insert(i, ' ')
            }
            val s2 = sb.toString()
            if (s2 != s.toString()) {
                self = true
                s?.replace(0, s.length, s2)
                self = false
            }
        }
    }
}

