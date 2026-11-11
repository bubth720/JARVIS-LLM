package fr.jarvis.llm.engine;

/** Declares the historical pathname loader signature; binding it to libjarvis_llm_a02 must fail. */
public final class LegacyPathProbe {
    public native void load(long h, byte[] path, int context, int threads, boolean small);
}
