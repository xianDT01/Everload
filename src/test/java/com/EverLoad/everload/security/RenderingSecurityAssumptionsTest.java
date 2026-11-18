package com.everload.everload.security;

import com.everload.everload.controller.SpaController;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.*;

class RenderingSecurityAssumptionsTest {
    private static final List<String> REVIEW_REQUIRED = List.of(
            "org/springframework/web/servlet/view/xslt/",
            "org/springframework/web/servlet/mvc/method/annotation/SseEmitter",
            "org/springframework/http/codec/ServerSentEvent",
            "org/springframework/web/servlet/view/FragmentsRendering",
            "org/springframework/web/reactive/result/view/FragmentsRendering");

    @Test void applicationDoesNotUseFeaturesCoveredByTheTwoTemporaryScanExceptions() throws IOException {
        List<String> violations = new ArrayList<>();
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/everload/everload/**/*.class");
        int inspected = 0;
        for (var resource : resources) {
            if (resource.getURL().toString().contains("/test-classes/")) continue;
            inspected++;
            try (var input = resource.getInputStream()) {
                // Inspect bytecode, not source text, to cover factory methods and indirect type references.
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    void check(String value) {
                        if (value == null) return;
                        for (String feature : REVIEW_REQUIRED) {
                            if (value.replace('.', '/').contains(feature)) violations.add(resource.getFilename() + ": " + feature);
                        }
                    }
                    @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                        check(signature); check(parent);
                        for (String type : interfaces) check(type);
                    }
                    @Override public org.springframework.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        check(descriptor); check(signature); return null;
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        check(descriptor); check(signature);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) { check(type); }
                            @Override public void visitFieldInsn(int opcode, String owner, String field, String type) { check(owner); check(type); }
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) { check(owner); check(type); }
                            @Override public void visitLdcInsn(Object value) { check(String.valueOf(value)); }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(inspected > 100, "Production bytecode must be present for the security review");
        assertEquals(List.of(), violations, "Reassess docs/security-beta.md and .trivyignore.yaml before adding rendering features");
    }

    @Test void spaAlwaysUsesAnExplicitStaticViewEvenForUserSuppliedPaths() {
        assertEquals("forward:/index.html", new SpaController().forwardToAngular("untrusted", "input", "path"));
    }
}
