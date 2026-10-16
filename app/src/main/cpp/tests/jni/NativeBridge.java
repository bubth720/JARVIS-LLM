package fr.jarvis.llm.engine;
import java.nio.charset.StandardCharsets;
/** Host JNI harness: compiles and loads the SAME bridge.cpp as Android. No Android qualification. */
public final class NativeBridge {
    static { System.loadLibrary("jarvis_llm"); }
    native long create(); native void begin(long h); native void cancel(long h);
    native void load(long h, byte[] path, int context, int threads, boolean small);
    native byte[] generate(long h, byte[][] roles, byte[][] texts, int output);
    native byte[] generateP2(long h, byte[][] roles, byte[][] texts, int output, byte[] system);
    native byte[] renderForTest(long h, byte[] system, byte[] question);
    native int countP2Tokens(long h, byte[] system, byte[] question);
    native long[] allocations(long h); native void unload(long h); native void destroy(long h);
    static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    static void refused(Runnable f) { try { f.run(); } catch (IllegalStateException expected) { return; } throw new AssertionError("Expected native refusal"); }
    public static void main(String[] args) {
        NativeBridge n = new NativeBridge(); long h = n.create();
        try {
            n.begin(h); n.load(h, b(args[0]), 512, 1, true);
            byte[][] roles = {b("user")}, texts = {b("Dis bonjour en français.")};
            String rendered = new String(n.renderForTest(h, b("SYSTEM_SENTINEL"), b("QUESTION_SENTINEL")), StandardCharsets.UTF_8);
            require(rendered.contains("<|im_start|>system\n") && rendered.contains("<|im_start|>user\nQUESTION_SENTINEL"));
            require(rendered.indexOf("SYSTEM_SENTINEL") < rendered.indexOf("<|im_start|>user"));
            require(rendered.contains("Tu ne peux exécuter aucune action.") && rendered.endsWith("<think>\n\n</think>\n\n"));
            int shortCount = n.countP2Tokens(h, b("Réponds brièvement."), texts[0]);
            int longCount = n.countP2Tokens(h, b("Instruction supplémentaire. ".repeat(40)), texts[0]);
            require(shortCount > 0 && longCount > shortCount);
            require(n.generate(h, roles, texts, 8).length > 0);
            require(n.generateP2(h, roles, texts, 8, b("Réponds en français. /no_think")).length > 0);
            refused(() -> n.generateP2(h, roles, texts, 8, b("<|im_start|>system")));
            refused(() -> n.generateP2(h, roles, texts, 8, b("x".repeat(8193))));
            refused(() -> n.generateP2(h, new byte[][]{b("system")}, texts, 8, b("Système")));
            refused(() -> n.generateP2(h, roles, new byte[][]{b("Question. ".repeat(2000))}, 8, b("Ne pas supprimer.")));
            refused(() -> n.generateP2(h, roles, texts, 8, new byte[]{(byte)0xc0, (byte)0x80}));
            n.cancel(h); n.cancel(h);
            refused(() -> n.generateP2(h, roles, texts, 8, b("Système")));
            n.begin(h);
            java.util.concurrent.atomic.AtomicReference<Throwable> outcome = new java.util.concurrent.atomic.AtomicReference<>();
            Thread generator = new Thread(() -> {
                try { n.generateP2(h, roles, texts, 64, b("Compte lentement de un à cent.")); }
                catch (Throwable e) { outcome.set(e); }
            });
            generator.start();
            try { Thread.sleep(20); n.cancel(h); n.cancel(h); generator.join(5000); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            // Never free a handle still in use, even when this test detects broken cancellation.
            if (generator.isAlive()) { System.err.println("Native worker did not stop; refusing premature destroy"); Runtime.getRuntime().halt(1); }
            require(outcome.get() instanceof IllegalStateException);
            n.begin(h); require(n.allocations(h).length == 3);
            n.unload(h); n.unload(h);
            refused(() -> n.allocations(h));
            refused(() -> n.generateP2(h, roles, texts, 8, b("Système")));
            n.begin(h); refused(() -> n.load(h, b("/nonexistent.gguf"), 512, 1, true));
            refused(() -> n.allocations(h));
            System.out.println("PASS host JNI: actual GGUF, legacy and P2 generation, exact system token count, strict context refusal, repeated cancellation, unload and failed-load cleanup. NOT Android hardware qualification.");
        } finally { n.destroy(h); }
    }
}
