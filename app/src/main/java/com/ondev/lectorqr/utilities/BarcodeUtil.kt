package com.ondev.lectorqr.utilities

import android.graphics.RectF
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.ondev.lectorqr.R
import com.ondev.lectorqr.views.GraphicOverlay


fun buildBarcodeScanner(): BarcodeScanner {
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    return BarcodeScanning.getClient(options)
}

fun getProgressToMeetBarcodeSizeRequirement(
    overlay: GraphicOverlay,
    barcode: Barcode
): Float {
    val reticleBoxWidth = getBarcodeReticleBox(overlay).width()
    val barcodeWidth = overlay.translateX(barcode.boundingBox?.width()?.toFloat() ?: 0f)
    val requiredWidth = reticleBoxWidth * 80 / 100
    return (barcodeWidth / requiredWidth).coerceAtMost(1f)
}

fun getBarcodeReticleBox(overlay: GraphicOverlay): RectF {
    val overlayWidth = overlay.width.toFloat()
    val overlayHeight = overlay.height.toFloat()
    val boxWidth = overlay.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_size)
    val boxHeight = overlay.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_size)
    val cx = overlayWidth / 2
    val cy = overlayHeight / 2
    return RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)
}