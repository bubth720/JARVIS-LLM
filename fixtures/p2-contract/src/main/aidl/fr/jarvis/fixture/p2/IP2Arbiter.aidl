package fr.jarvis.fixture.p2;
import fr.jarvis.fixture.p2.IP2AuthorizationCallback;
oneway interface IP2Arbiter { void authorize(in byte[] query, IP2AuthorizationCallback callback); }
