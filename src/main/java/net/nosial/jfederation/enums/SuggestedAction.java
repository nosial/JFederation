package net.nosial.jfederation.enums;

/**
 * Recommended action produced by the content scanning system based on the assessed risk of scanned
 * content. The server returns one of these actions to guide the caller on the appropriate response.
 */
public enum SuggestedAction
{
    BLOCK_CONTENT,
    TEMPORARILY_BLOCK_ENTITY,
    PERMANENTLY_BLOCK_ENTITY,
    CAUTION
}
