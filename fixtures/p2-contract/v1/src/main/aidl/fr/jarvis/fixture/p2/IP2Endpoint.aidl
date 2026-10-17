package fr.jarvis.fixture.p2;
interface IP2Endpoint {
    byte[] hello(in byte[] payload, IBinder clientLiveness);
    byte[] submit(in byte[] payload);
    byte[] status(in byte[] key);
    byte[] cancel(in byte[] key);
}
