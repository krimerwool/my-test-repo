so i the android application and I am detecting fallow and crops(possibly multiple) and their respective locations in the image, and in that i am trying to detect the confidence with which an image is being detected as fallow, the streamlit app is displaying the correct confidence for the fallow/barren classification and thus i am also providing you with the ocde for that for your reference:
(here you will often see me use the term barren in place of fallow but mind you that they are the same)::
    barren_interp.set_tensor(
        barren_in[0]["index"],
        preprocess_barren(image)
    )
    barren_interp.invoke()

    prob_crop = barren_interp.get_tensor(
        barren_out[0]["index"]
    )[0][0]

    barren_result = {
        "is_crop": prob_crop > 0.5,
        "confidence": float(prob_crop if prob_crop > 0.5 else 1 - prob_crop)
    }

this is the correct logic for detecting that but the logic to in my actual application code file is:
package com.example.cropanalysissdk

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType

class ModelEngine(context: Context) {

    private var barrenInterpreter: Interpreter? = null
    private var cropInterpreter: Interpreter? = null

    // Labels (Maize=0, Rice=1, Soybean=2, Sugarcane=3)
    private val cropLabels = listOf("Maize", "Rice", "Soybean", "Sugarcane")

    init {
        val options = Interpreter.Options().apply { numThreads = 4 }

        // Load models with correct filenames
        barrenInterpreter = Interpreter(FileUtil.loadMappedFile(context, "barren_vs_crop_model_v3.tflite"), options)
        cropInterpreter = Interpreter(FileUtil.loadMappedFile(context, "phase1_model.tflite"), options)
    }

    /**
     * 🔴 FIXED: Corrected barren detection logic to match Python/Streamlit
     *
     * Barren Model Output Interpretation:
     * - prob > 0.5  → Non-Barren (CROP present)
     * - prob ≤ 0.5  → Barren land
     *
     * @return Pair(isBarren: Boolean, confidence: Float)
     */
    fun isBarren(bitmap: Bitmap): Pair<Boolean, Float> {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            // NO NORMALIZATION: Input stays 0-255 (matches training)
            .build()

        var tImage = TensorImage(DataType.FLOAT32)
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
        barrenInterpreter?.run(tImage.buffer, outputBuffer.buffer.rewind())

        val prob = outputBuffer.floatArray[0]

        // Safety: If NaN, assume Crop (safer fallback)
        if (prob.isNaN()) return Pair(false, 0.5f)

        // 🔴 CORRECTED LOGIC:
        // prob > 0.5 means CROP (non-barren)
        // prob ≤ 0.5 means BARREN
        val isCrop = prob > 0.5f
        val isBarren = !isCrop

        // Confidence = distance from decision boundary
        val confidence = if (isCrop) prob else (1.0f - prob)

        return Pair(isBarren, confidence)
    }

    /**
     * Classifies the crop type from a bitmap.
     *
     * @return Pair(cropName: String, confidence: Float)
     */
    fun classifyCrop(bitmap: Bitmap): Pair<String, Float> {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(260, 260, ResizeOp.ResizeMethod.BILINEAR))
            // NO NORMALIZATION: Input stays 0-255 (model has internal preprocessing)
            .build()

        var tImage = TensorImage(DataType.FLOAT32)
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 4), DataType.FLOAT32)
        cropInterpreter?.run(tImage.buffer, outputBuffer.buffer.rewind())

        val probs = outputBuffer.floatArray

        var maxIdx = -1
        var maxProb = -1.0f

        for (i in probs.indices) {
            // NaN Safety
            if (probs[i].isNaN()) continue

            if (probs[i] > maxProb) {
                maxProb = probs[i]
                maxIdx = i
            }
        }

        return if (maxIdx != -1 && maxProb > 0.0f) {
            Pair(cropLabels[maxIdx], maxProb)
        } else {
            Pair("Unknown", 0f)
        }
    }
}

here there is an error which is making the confidence stay close to 50 % always, help me fix that flaw
