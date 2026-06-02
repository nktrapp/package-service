package br.furb.pkg.domain.exception;

public class InvalidPackageStateException extends RuntimeException {

    public InvalidPackageStateException(String message) {
        super(message);
    }
}
