package fr.jarvis.llm.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Model-free host harness. Loads the approved bridge itself, with no substitute llama implementation. */
public final class NativeBridge {
    static { System.loadLibrary("jarvis_llm"); }
    native long create();
    native void begin(long h);
    native void cancel(long h);
    native void load(long h, byte[] path, int context, int threads, boolean small);
    native byte[] generate(long h, byte[][] roles, byte[][] texts, int output);
    native byte[] generateP2(long h, byte[][] roles, byte[][] texts, int output, byte[] system);
    native byte[] renderForTest(long h, byte[] system, byte[] question);
    native byte[][] measureFixtureP2(long h, byte[] system, byte[] question);
    native byte[] generateFixtureP2(long h, byte[] system, byte[] question, int output, int contextLimit);
    native int countP2Tokens(long h, byte[] system, byte[] question);
    native long[] allocations(long h);
    native void unload(long h);
    native void destroy(long h);
    native int generateStream(long h, byte[] system, byte[] question, int output,
                             int contextLimit, byte[] binding, Object callback);

    static final int ACCEPTED = 0, RETRY = 1, ERROR = 4, CANCELLED = 3;
    static final byte[] BINDING = b("a".repeat(64));
    static final byte[] SYSTEM = b("Public model-free JNI fixture");
    static final byte[] QUESTION = b("This fixture must not generate text");
    static byte[] b(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    interface Checked { void run() throws Throwable; }
    interface WithHandle { void run(long h) throws Throwable; }
    static void expected(Class<? extends Throwable> type, Checked body) throws Throwable {
        try { body.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception class: " + error.getClass().getName(), error);
        }
        throw new AssertionError("Expected " + type.getName());
    }
    static void sameException(Throwable marker, Checked body) throws Throwable {
        try { body.run(); }
        catch (Throwable error) { require(error == marker, "Original callback exception was replaced"); return; }
        throw new AssertionError("Expected injected callback exception");
    }
    static final NativeBridge N = new NativeBridge();
    static void handle(WithHandle body) throws Throwable {
        long h = N.create();
        require(h > 0, "Positive opaque handle required");
        try { N.begin(h); body.run(h); }
        finally { N.destroy(h); }
    }
    static int stream(long h, Object callback) {
        return N.generateStream(h, SYSTEM, QUESTION, 8, 512, BINDING, callback);
    }

    static final class Event {
        final byte[] binding, payload;
        final int kind, sequence, sampled, domain;
        final long first, thread;
        Event(byte[] binding, int kind, int sequence, byte[] payload, int sampled, long first, int domain) {
            this.binding = binding.clone(); this.kind = kind; this.sequence = sequence;
            this.payload = payload.clone(); this.sampled = sampled; this.first = first; this.domain = domain;
            this.thread = Thread.currentThread().threadId();
        }
    }
    public static class Spy {
        final List<Event> events = new ArrayList<>();
        int preparedCalls;
        public boolean onNativePrepared(byte[] rendered, byte[] template, int inputTokens) {
            ++preparedCalls; return true;
        }
        public int onNativeEvent(byte[] binding, int kind, int sequence, byte[] payload,
                                 int sampled, long first, int domain) {
            events.add(new Event(binding, kind, sequence, payload, sampled, first, domain)); return ACCEPTED;
        }
        void onlyTerminal(int expectedKind) {
            require(preparedCalls == 0, "No model may prepare a prompt");
            require(events.size() == 1, "Exactly one model-free terminal callback required");
            Event event = events.get(0);
            require(event.kind == expectedKind && event.sequence == 0, "Unexpected model-free terminal");
            require(event.payload.length == 0 && event.sampled == 0 && event.first == -1,
                    "No generated text or native first-token timestamp exists without a model");
            require(event.domain == 1 && Arrays.equals(event.binding, BINDING), "Host clock/binding mismatch");
        }
    }
    public static final class MissingPrepared {
        public int onNativeEvent(byte[] binding, int kind, int sequence, byte[] payload,
                                 int sampled, long first, int domain) { return ACCEPTED; }
    }
    public static final class MissingEvent {
        public boolean onNativePrepared(byte[] rendered, byte[] template, int inputTokens) { return true; }
    }

    static final List<String> PASSED = new ArrayList<>();
    static void test(String id, Checked body) throws Throwable {
        body.run(); PASSED.add(id); System.out.println("PASS " + id);
    }
    static void neverDestroyRunningThread(Thread thread) throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            System.err.println("CRITICAL native callback thread did not return; refusing premature destroy");
            Runtime.getRuntime().halt(91);
        }
    }
    static void suite() throws Throwable {
        test("empty_engine_lifecycle", () -> handle(h -> {
            N.cancel(h); N.cancel(h); N.begin(h); N.unload(h); N.unload(h); N.begin(h);
        }));
        test("absent_model_error_callback_has_no_tokens", () -> handle(h -> {
            Spy spy = new Spy(); require(stream(h, spy) == ERROR, "Missing model must not complete"); spy.onlyTerminal(ERROR);
        }));
        test("cancel_before_entry_is_cancelled_without_content", () -> handle(h -> {
            N.cancel(h); Spy spy = new Spy(); require(stream(h, spy) == CANCELLED, "Cancellation must survive entry");
            spy.onlyTerminal(CANCELLED);
        }));
        test("callback_is_synchronous_on_calling_thread", () -> handle(h -> {
            Spy spy = new Spy(); long caller = Thread.currentThread().threadId(); stream(h, spy); spy.onlyTerminal(ERROR);
            require(spy.events.get(0).thread == caller, "A second native callback worker was introduced");
        }));
        test("callback_java_exception_identity_is_preserved", () -> handle(h -> {
            RuntimeException marker = new RuntimeException("INJECTED_CALLBACK_EXCEPTION");
            Spy spy = new Spy() { @Override public int onNativeEvent(byte[] a,int b,int c,byte[] d,int e,long f,int g) { throw marker; } };
            sameException(marker, () -> stream(h, spy)); N.begin(h); N.unload(h);
        }));
        test("callback_injected_oom_identity_is_preserved", () -> handle(h -> {
            OutOfMemoryError marker = new OutOfMemoryError("INJECTED_CALLBACK_OOM_NOT_REAL_HEAP_EXHAUSTION");
            Spy spy = new Spy() { @Override public int onNativeEvent(byte[] a,int b,int c,byte[] d,int e,long f,int g) { throw marker; } };
            sameException(marker, () -> stream(h, spy)); N.begin(h); N.unload(h);
        }));
        test("missing_prepared_descriptor_preserves_jvm_error", () -> handle(h -> {
            expected(NoSuchMethodError.class, () -> stream(h, new MissingPrepared())); N.begin(h);
        }));
        test("missing_event_descriptor_preserves_jvm_error", () -> handle(h -> {
            expected(NoSuchMethodError.class, () -> stream(h, new MissingEvent())); N.begin(h);
        }));
        test("short_binding_is_refused_before_callback", () -> handle(h -> {
            Spy spy = new Spy(); expected(IllegalStateException.class,
                () -> N.generateStream(h,SYSTEM,QUESTION,8,512,b("a"),spy)); require(spy.events.isEmpty(), "Unexpected callback");
        }));
        test("non_hex_binding_is_refused_before_callback", () -> handle(h -> {
            Spy spy = new Spy(); expected(IllegalStateException.class,
                () -> N.generateStream(h,SYSTEM,QUESTION,8,512,b("G".repeat(64)),spy)); require(spy.events.isEmpty(), "Unexpected callback");
        }));
        test("null_callback_is_refused", () -> handle(h -> expected(IllegalStateException.class, () -> stream(h,null))));
        test("null_binding_is_refused", () -> handle(h -> expected(IllegalStateException.class,
            () -> N.generateStream(h,SYSTEM,QUESTION,8,512,null,new Spy()))));
        test("unknown_handle_is_refused_without_dereference", () -> {
            expected(IllegalStateException.class, () -> N.begin(Long.MAX_VALUE));
            expected(IllegalStateException.class, () -> N.cancel(-1));
            expected(IllegalStateException.class, () -> stream(0,new Spy())); N.destroy(0);
        });
        test("destroyed_handle_is_never_reused", () -> {
            long previous = 0;
            for (int i=0;i<256;++i) {
                long h=N.create(); require(h>previous, "Opaque handle was reused"); N.begin(h); N.unload(h); N.destroy(h);
                expected(IllegalStateException.class, () -> N.begin(h));
                expected(IllegalStateException.class, () -> N.cancel(h));
                expected(IllegalStateException.class, () -> N.destroy(h)); previous=h;
            }
        });
        test("legacy_signatures_are_bound_and_refuse_not_ready", () -> handle(h -> {
            byte[][] roles={b("user")}, texts={QUESTION};
            expected(IllegalStateException.class, () -> N.generate(h,roles,texts,8));
            expected(IllegalStateException.class, () -> N.generateP2(h,roles,texts,8,SYSTEM));
            expected(IllegalStateException.class, () -> N.countP2Tokens(h,SYSTEM,QUESTION));
            expected(IllegalStateException.class, () -> N.measureFixtureP2(h,SYSTEM,QUESTION));
            expected(IllegalStateException.class, () -> N.generateFixtureP2(h,SYSTEM,QUESTION,8,512));
            expected(IllegalStateException.class, () -> N.renderForTest(h,SYSTEM,QUESTION));
            expected(IllegalStateException.class, () -> N.allocations(h)); N.begin(h);
        }));
        test("failed_load_releases_call_pin_without_fake_model", () -> handle(h -> {
            expected(IllegalStateException.class, () -> N.load(h,b("/jarvis-public-fixture/absent.gguf"),512,1,true));
            N.begin(h); N.unload(h); expected(IllegalStateException.class, () -> N.allocations(h));
        }));
        test("callback_reentrancy_refuses_cleanup_but_allows_cancel", () -> handle(h -> {
            Spy spy = new Spy() { @Override public int onNativeEvent(byte[] a,int b,int c,byte[] d,int e,long f,int g) {
                try {
                    expected(IllegalStateException.class, () -> N.destroy(h));
                    expected(IllegalStateException.class, () -> N.unload(h));
                    expected(IllegalStateException.class, () -> N.begin(h)); N.cancel(h); N.cancel(h);
                } catch (Throwable error) { throw new AssertionError(error); }
                return super.onNativeEvent(a,b,c,d,e,f,g);
            }};
            require(stream(h,spy)==ERROR, "Error terminal already entered must remain ERROR"); spy.onlyTerminal(ERROR); N.begin(h);
        }));
        test("blocked_callback_keeps_pin_until_actual_return", () -> handle(h -> {
            CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
            AtomicReference<Throwable> workerError=new AtomicReference<>(); AtomicInteger terminal=new AtomicInteger(-1);
            Spy spy=new Spy() { @Override public int onNativeEvent(byte[] a,int b,int c,byte[] d,int e,long f,int g) {
                entered.countDown();
                try { require(release.await(8,TimeUnit.SECONDS), "Test consumer was not released"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                return super.onNativeEvent(a,b,c,d,e,f,g);
            }};
            Thread worker=new Thread(() -> { try { terminal.set(stream(h,spy)); } catch(Throwable error) { workerError.set(error); } }, "perf03b-jni-caller");
            worker.start();
            try {
                require(entered.await(5,TimeUnit.SECONDS), "Native callback was not entered");
                expected(IllegalStateException.class, () -> N.destroy(h));
                expected(IllegalStateException.class, () -> N.unload(h));
                expected(IllegalStateException.class, () -> N.begin(h));
                expected(IllegalStateException.class, () -> N.allocations(h));
                expected(IllegalStateException.class, () -> N.load(h,b("/jarvis-public-fixture/absent.gguf"),512,1,true));
                expected(IllegalStateException.class, () -> stream(h,new Spy()));
                N.cancel(h); N.cancel(h); require(worker.isAlive(), "Cancel fabricated native callback completion");
            } finally { release.countDown(); neverDestroyRunningThread(worker); }
            require(workerError.get()==null && terminal.get()==ERROR, "Native callback return failed");
            spy.onlyTerminal(ERROR); N.begin(h); N.unload(h);
        }));
        test("bounded_repeated_callbacks_do_not_exhaust_local_refs", () -> handle(h -> {
            for(int i=0;i<512;++i) { N.begin(h); Spy spy=new Spy(); require(stream(h,spy)==ERROR,"Unexpected success"); spy.onlyTerminal(ERROR); }
        }));
        test("terminal_rejection_is_not_retried_as_content", () -> handle(h -> {
            Spy spy=new Spy() { @Override public int onNativeEvent(byte[] a,int b,int c,byte[] d,int e,long f,int g) {
                super.onNativeEvent(a,b,c,d,e,f,g); return RETRY;
            }};
            require(stream(h,spy)==ERROR,"Rejected terminal must not complete"); spy.onlyTerminal(ERROR); N.begin(h);
        }));
        test("no_callback_after_return_unload_and_destroy", () -> {
            long h=N.create(); N.begin(h); Spy spy=new Spy(); require(stream(h,spy)==ERROR,"Unexpected success");
            N.unload(h); N.destroy(h); spy.onlyTerminal(ERROR);
        });
        test("double_cancel_is_sticky_until_explicit_begin", () -> handle(h -> {
            N.cancel(h); N.cancel(h); Spy first=new Spy(),second=new Spy();
            require(stream(h,first)==CANCELLED && stream(h,second)==CANCELLED,"Cancel reset without begin");
            first.onlyTerminal(CANCELLED); second.onlyTerminal(CANCELLED); N.begin(h);
            Spy rearmed=new Spy(); require(stream(h,rearmed)==ERROR,"Begin did not rearm"); rearmed.onlyTerminal(ERROR);
        }));
    }
    static String quote(String value) { return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    public static void main(String[] args) {
        try {
            suite();
            System.out.println("{\"schema\":\"jarvis.perf03b.jni-host/1\",\"passed\":"+PASSED.size()+
                ",\"failed\":0,\"cases\":["+String.join(",",PASSED.stream().map(NativeBridge::quote).toList())+
                "],\"jni\":\"REAL_APPROVED_BRIDGE\",\"model\":\"NOT_RUN\",\"real_token_stream\":\"NOT_RUN\",\"prefill_decode\":\"NOT_RUN\",\"ttft\":\"NOT_RUN\"}");
        } catch(Throwable failure) {
            failure.printStackTrace(System.err);
            System.out.println("{\"schema\":\"jarvis.perf03b.jni-host/1\",\"passed\":"+PASSED.size()+
                ",\"failed\":1,\"critical\":true,\"jni\":\"REAL_APPROVED_BRIDGE\",\"model\":\"NOT_RUN\",\"qualification\":\"STOPPED\"}");
            System.exit(1);
        }
    }
}
