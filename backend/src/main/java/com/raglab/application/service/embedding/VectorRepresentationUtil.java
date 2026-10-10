package com.raglab.application.service.embedding;

import java.util.Arrays;

/**
 * Matryoshka Representation Learning (MRL) dimension truncator and vector quantizer.
 */
public class VectorRepresentationUtil {

    /**
     * Truncates a normalized high-dimensional vector to target Matryoshka dimensions
     * and re-normalizes to unit L2 norm as required by cosine similarity.
     */
    public static float[] truncateMatryoshka(float[] vector, int targetDim) {
        if (vector == null) return null;
        if (targetDim >= vector.length) return Arrays.copyOf(vector, vector.length);
        if (targetDim <= 0) throw new IllegalArgumentException("Target dimension must be positive: " + targetDim);

        float[] truncated = Arrays.copyOf(vector, targetDim);
        double normSq = 0.0;
        for (float v : truncated) {
            normSq += (double) v * v;
        }

        if (normSq > 0.0) {
            float norm = (float) Math.sqrt(normSq);
            for (int i = 0; i < targetDim; i++) {
                truncated[i] /= norm;
            }
        }
        return truncated;
    }

    /**
     * 1-bit binary vector quantization: sign-based thresholding (v >= 0 ? 1 : 0).
     * Returns a bit string representation (e.g., "101100...") compatible with PostgreSQL bit varying.
     */
    public static String quantizeToBinaryBitString(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder(vector.length);
        for (float val : vector) {
            sb.append(val >= 0.0f ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Converts float array to half-precision representation string compatible with pgvector halfvec.
     */
    public static String toHalfvecString(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.US, "%.5f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Converts float array to standard vector string.
     */
    public static String toVectorString(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.US, "%.7f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}

