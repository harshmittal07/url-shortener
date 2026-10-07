package io.github.harshmittal.urlshortener.link.domain;

/** Every attempt collided with an existing code (R7). */
public final class CodeGenerationFailedException extends RuntimeException {

    public CodeGenerationFailedException(int attempts) {
        super("No free short code after " + attempts + " attempts");
    }
}
