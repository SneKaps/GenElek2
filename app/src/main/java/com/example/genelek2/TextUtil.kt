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

internal object TextUtil {
    @ColorInt
    var caretBackground: Int = -0x99999a

    const val newline_crlf: String = "\r\n"
    const val newline_lf: String = "\n"

    fun fromHexString(s: CharSequence): ByteArray {
        val buf = ByteArrayOutputStream()
        var b: Byte = 0
        var nibble = 0
        for (pos in 0 until s.length) {
            if (nibble == 2) {
                buf.write(b.toInt())
                nibble = 0
                b = 0
            }
            val c = s[pos].code
            if (c >= '0'.code && c <= '9'.code) {
                nibble++
                b = (b * 16).toByte()
                b = (b + (c - '0'.code)).toByte()
            }
            if (c >= 'A'.code && c <= 'F'.code) {
                nibble++
                b = (b * 16).toByte()
                b = (b + (c - 'A'.code + 10)).toByte()
            }
            if (c >= 'a'.code && c <= 'f'.code) {
                nibble++
                b = (b * 16).toByte()
                b = (b + (c - 'a'.code + 10)).toByte()
            }
        }
        if (nibble > 0) buf.write(b.toInt())
        return buf.toByteArray()
    }

    fun ByteArray.toHexString(begin: Int = 0, end: Int = this.size, delimiter: String = ""): String {
        require(begin in 0..size && end in 0..size && begin <= end) {
            "Invalid range: begin=$begin, end=$end, size=$size"
        }
        val sb = StringBuilder((end - begin) * (2 + delimiter.length))
        for (i in begin until end) {
            if (i > begin) sb.append(delimiter)
            sb.append(String.format("%02X", this[i]))
        }
        return sb.toString()
    }






    /*
    @JvmOverloads
    fun toHexString(buf: ByteArray, begin: Int = 0, end: Int = buf.length): String {
        val sb = StringBuilder(3 * (end - begin))
        toHexString(sb, buf, begin, end)
        return sb.toString()
    }

     */

    @JvmOverloads
    fun toHexString(sb: StringBuilder, buf: ByteArray ,begin: Int = 0, end: Int = buf.size) {
        for (pos in begin until end) {
            if (sb.isNotEmpty()) sb.append(' ')
            var c = (buf[pos].toInt() and 0xff) / 16
            c += if (c >= 10) 'A'.code - 10
            else '0'.code
            sb.append(c.toChar())
            c = (buf[pos].toInt() and 0xff) % 16
            c += if (c >= 10) 'A'.code - 10
            else '0'.code
            sb.append(c.toChar())
        }
    }

    /**
     * use https://en.wikipedia.org/wiki/Caret_notation to avoid invisible control characters
     */
    @JvmOverloads
    fun toCaretString(s: CharSequence, keepNewline: Boolean, length: Int = s.length): CharSequence {
        var found = false
        for (pos in 0 until length) {
            if (s[pos].code < 32 && (!keepNewline || s[pos] != '\n')) {
                found = true
                break
            }
        }
        if (!found) return s
        val sb = SpannableStringBuilder()
        for (pos in 0 until length) if (s[pos].code < 32 && (!keepNewline || s[pos] != '\n')) {
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
        return sb
    }

    internal class HexWatcher(private val view: TextView) : TextWatcher {
        private val sb = StringBuilder()
        private var self = false
        private var enabled = false

        fun enable(enable: Boolean) {
            if (enable) {
                view.inputType =
                    InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                view.inputType = InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            enabled = enable
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            if (!enabled || self) return

            sb.delete(0, sb.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c >= '0' && c <= '9') sb.append(c)
                if (c >= 'A' && c <= 'F') sb.append(c)
                if (c >= 'a' && c <= 'f') sb.append((c.code + 'A'.code - 'a'.code).toChar())
                i++
            }
            i = 2
            while (i < sb.length) {
                sb.insert(i, ' ')
                i += 3
            }
            val s2 = sb.toString()

            if (s2 != s.toString()) {
                self = true
                s.replace(0, s.length, s2)
                self = false
            }
        }
    }
}