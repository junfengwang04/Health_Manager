package com.pbz.healthmanager.analysis

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions


/**
 * 药品 OCR 分析器
 * 使用 Google ML Kit 进行中文文本识别
 */
class MedicineOcrAnalyzer(private val onResult: (OcrResult) -> Unit) {

    /**
     * OCR 识别结果数据类
     * @property fullText 识别到的完整纯文本
     * @property approvalNumber 提取出的国药准字 (如果匹配成功)
     */
    data class OcrResult(
        val fullText: String,
        val approvalNumber: String?
    )

    // 初始化中文文本识别客户端
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    // 国药准字正则匹配：国药准字[Z|H|J]\d{8}
    private val approvalNumberRegex = Regex("国药准字[ZHJ]\\d{8}")

    /**
     * 分析传入的图片 Bitmap
     */
    fun analyze(bitmap: Bitmap) {
        // 1. 图片预处理：确保图片大小适中，防止内存溢出
        val processedBitmap = preprocessBitmap(bitmap)
        
        // 2. 将 Bitmap 转换为 ML Kit 所需的 InputImage
        val image = InputImage.fromBitmap(processedBitmap, 0)

        // 3. 执行文本识别
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                // 4. 提取国药准字
                val approvalNumber = extractApprovalNumber(fullText)
                
                Log.d("MedicineOcrAnalyzer", "OCR Full Text: $fullText")
                Log.d("MedicineOcrAnalyzer", "Extracted Approval Number: $approvalNumber")
                
                // 5. 通过回调返回结果
                onResult(OcrResult(fullText, approvalNumber))
            }
            .addOnFailureListener { e ->
                Log.e("MedicineOcrAnalyzer", "OCR failed: ${e.message}", e)
                // 识别失败返回空结果
                onResult(OcrResult("", null))
            }
    }

    /**
     * 图片预处理
     * 针对中低端机型进行缩放，防止 InputImage 过大导致内存溢出
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val maxDimension = 1280 // 设置最大维度为 1280 像素
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / Math.max(width, height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
        }

        Log.d("MedicineOcrAnalyzer", "Preprocessing: Scaling down from ${width}x${height} to ${ (width * scale).toInt() }x${ (height * scale).toInt() }")
        
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * 从完整文本中提取符合“国药准字[Z|H|J]\d{8}”格式的文本
     */
    private fun extractApprovalNumber(text: String): String? {
        // 去除空格后进行正则匹配，防止 OCR 识别时产生的零碎空格干扰
        val cleanText = text.replace("\\s".toRegex(), "")
        return approvalNumberRegex.find(cleanText)?.value
    }

    /**
     * 释放资源
     */
    fun close() {
        recognizer.close()
    }
}
