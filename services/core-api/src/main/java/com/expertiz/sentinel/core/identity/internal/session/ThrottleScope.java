package com.expertiz.sentinel.core.identity.internal.session;

/** Portées de la limitation des tentatives et leur seuil de blocage (spec §8.4). */
public enum ThrottleScope {

    /** Un compte depuis une IP : bloquer un compte à distance resterait impossible depuis une autre IP. */
    ACCOUNT_IP(5),
    /** Une IP, tous comptes confondus. */
    IP(20);

    private final int threshold;

    ThrottleScope(int threshold) {
        this.threshold = threshold;
    }

    public int threshold() {
        return threshold;
    }
}
