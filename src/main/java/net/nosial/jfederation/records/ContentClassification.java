package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.ClassificationFlag;

/**
 * Outcome of classifying scanned content, including the overall classification flag, confidence
 * score, and the language detected in the content.
 *
 * @param classificationFlag the overall classification (malicious, suspicious, or normal)
 * @param confidence the confidence level of the classification (0.0 to 1.0)
 * @param detectedLanguage the language detected in the content, or {@code null}
 */
public record ContentClassification(
    @JsonProperty("classification_flag") ClassificationFlag classificationFlag,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("detected_language") String detectedLanguage)
{ }
