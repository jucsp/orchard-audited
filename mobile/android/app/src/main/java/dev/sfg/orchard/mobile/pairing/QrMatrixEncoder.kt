// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A dense boolean grid; `true` is a dark module. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) { "matrix must be square" }
    }

    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size) { "coordinates out of range" }
        return dark[y * size + x]
    }
}

/** Pluggable QR encoder so the D3 decision stays local to one implementation (SDD §4 D3). */
fun interface QrMatrixEncoder {
    fun encode(text: String): QrMatrix
}

/** ZXing core encoder: Java-only, no network, no Play Services (PAIR-INV-3). */
class ZxingQrMatrixEncoder : QrMatrixEncoder {
    override fun encode(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 4,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val dark = BooleanArray(matrix.width * matrix.height) { index ->
            matrix.get(index % matrix.width, index / matrix.width)
        }
        return QrMatrix(matrix.width, dark)
    }
}
