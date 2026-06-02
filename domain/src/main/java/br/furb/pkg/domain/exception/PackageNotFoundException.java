package br.furb.pkg.domain.exception;

public class PackageNotFoundException extends RuntimeException {

    public PackageNotFoundException(String packageId) {
        super("Package not found for id: " + packageId);
    }
}
