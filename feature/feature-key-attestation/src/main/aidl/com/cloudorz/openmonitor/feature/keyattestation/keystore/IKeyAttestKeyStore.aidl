package com.cloudorz.openmonitor.feature.keyattestation.keystore;

interface IKeyAttestKeyStore {
    // Returns null on success, serialized exception bytes on failure.
    byte[] generateKeyPair(String alias, String attestKeyAlias, boolean useStrongBox);
    // Returns concatenated DER-encoded certs, or null if alias not found.
    byte[] getCertificateChain(String alias);
    boolean containsAlias(String alias);
    void deleteEntry(String alias);
    void deleteAllEntries();
}
