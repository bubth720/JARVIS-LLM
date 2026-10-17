package fr.jarvis.fixture.p2;
import fr.jarvis.fixture.p2.IP2AuthorizationCallback;
import fr.jarvis.fixture.p2.IRecoveryCallback;
oneway interface IP2Arbiter {
    void authorize(in byte[] query, IP2AuthorizationCallback callback);
    void recovery(in byte[] message, IBinder peerLiveness, IRecoveryCallback callback);
}
