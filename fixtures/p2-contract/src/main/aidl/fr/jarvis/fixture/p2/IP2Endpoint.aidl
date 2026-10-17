package fr.jarvis.fixture.p2;
import fr.jarvis.fixture.p2.IRecoveryCallback;
interface IP2Endpoint {
    byte[] hello(in byte[] payload, IBinder clientLiveness);
    byte[] submit(in byte[] payload);
    byte[] status(in byte[] key);
    byte[] cancel(in byte[] key);
    void recovery(in byte[] message, IBinder peerLiveness, IRecoveryCallback callback);
}
