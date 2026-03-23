package me.katze225;

import lombok.Getter;
import me.katze225.object.ObfuscatorSettings;
import me.katze225.transformer.ITransformer;
import me.katze225.transformer.impl.BooleansTransformer;
import me.katze225.transformer.impl.DispatcherTransformer;
import me.katze225.transformer.impl.ExpressionTransformer;
import me.katze225.transformer.impl.NumbersTransformer;
import me.katze225.transformer.impl.ShuffleTransformer;
import me.katze225.transformer.impl.StringTransformer;
import me.katze225.transformer.impl.TrashTransformer;
import me.katze225.utility.StringUtility;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class Obfuscator {
    @Getter
    private ObfuscatorSettings settings;
    @Getter
    private String prefix;
    private Map<String, ClassNode> classes = new HashMap<>();
    private static final int CLASS_READER_FLAGS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    public Obfuscator(ObfuscatorSettings settings) {
        this.settings = settings;
        this.prefix = StringUtility.randomPrefix();
    }

    public void run() {
        try {
            loadClasses();
            applyTransformers();
            writeOutput();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadClasses() throws IOException {
        System.out.println("Reading JAR file: " + settings.getInputPath());
        Path inputPath = Paths.get(settings.getInputPath());
        if (!Files.exists(inputPath)) {
            throw new IOException("Input JAR file not found: " + settings.getInputPath());
        }
        JarFile jarFile = new JarFile(settings.getInputPath());
        jarFile.stream().forEach(entry -> {
            try {
                if (entry.getName().endsWith(".class")) {
                    final ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
                    final ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, CLASS_READER_FLAGS);
                    classes.put(classNode.name, classNode);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
        jarFile.close();
        System.out.println("Loaded " + classes.size() + " classes");
    }

    private void applyTransformers() {
        List<ITransformer> transformers = buildTransformers();
        for (ITransformer transformer : transformers) {
            transformer.setPrefix(prefix);
        }
        for (ITransformer transformer : transformers) {
            System.out.println("Applying " + transformer.getClass().getSimpleName() + "...");
            for (ClassNode classNode : classes.values()) {
                try {
                    transformer.modify(classNode);
                } catch (Exception e) {
                    System.err.println("Error applying transformer to class: " + classNode.name);
                    e.printStackTrace();
                }
            }
        }
    }

    private List<ITransformer> buildTransformers() {
        List<ITransformer> transformers = new ArrayList<>();
        if (settings.isEnabledTrash()) {
            transformers.add(new TrashTransformer());
        }
        if (settings.isEnabledString()) {
            transformers.add(new StringTransformer());
        }
        if (settings.isEnabledNumbers()) {
            transformers.add(new NumbersTransformer());
        }
        if (settings.isEnabledBoolean()) {
            transformers.add(new BooleansTransformer());
        }
        if (settings.isEnabledFlow()) {
            transformers.add(new ExpressionTransformer());
        }
        if (settings.isEnabledDispatcher()) {
            transformers.add(new DispatcherTransformer());
        }
        if (settings.isEnabledShuffle()) {
            transformers.add(new ShuffleTransformer());
        }
        return transformers;
    }

    private void writeOutput() throws IOException {
        System.out.println("Writing JAR file: " + settings.getOutputPath());
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(settings.getOutputPath()))) {
            Path inputPath = Paths.get(settings.getInputPath());
            if (!Files.exists(inputPath)) {
                throw new IOException("Input JAR file not found: " + settings.getInputPath());
            }
            JarFile inputJar = new JarFile(settings.getInputPath());
            
            inputJar.stream().forEach(entry -> {
                try {
                    if (!entry.getName().endsWith(".class") && !entry.isDirectory()) {
                        jarOut.putNextEntry(new ZipEntry(entry.getName()));
                        jarOut.write(toByteArray(inputJar.getInputStream(entry)));
                        jarOut.closeEntry();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            inputJar.close();

            for (ClassNode classNode : classes.values()) {
                stripFrames(classNode);
                ClassWriter classWriter = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                try {
                    classNode.accept(classWriter);
                    final JarEntry jarEntry = new JarEntry(classNode.name.concat(".class"));
                    jarOut.putNextEntry(jarEntry);
                    jarOut.write(classWriter.toByteArray());
                    jarOut.closeEntry();
                } catch (Exception e) {
                    System.err.println("Error writing class: " + classNode.name);
                    e.printStackTrace();
                }
            }

            if (settings.isEnabledZipComment()) {
                jarOut.setComment(settings.getZipCommentText());
            }
        }
        System.out.println("JAR file written successfully");
    }

    private void stripFrames(ClassNode classNode) {
        if (classNode.methods == null) {
            return;
        }
        for (MethodNode method : classNode.methods) {
            if (method.instructions != null) {
                method.instructions.forEach(insn -> {
                    if (insn instanceof org.objectweb.asm.tree.FrameNode) {
                        method.instructions.remove(insn);
                    }
                });
            }
        }
    }

    private byte[] toByteArray(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private class CustomClassWriter extends ClassWriter {
        public CustomClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object")) {
                return "java/lang/Object";
            }

            ClassNode class1 = classes.get(type1);
            ClassNode class2 = classes.get(type2);

            if (class1 == null || class2 == null) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }

            if (isAssignableFrom(type1, type2)) {
                return type1;
            }
            if (isAssignableFrom(type2, type1)) {
                return type2;
            }

            String current = class1.superName;
            while (current != null && !current.equals("java/lang/Object")) {
                if (isAssignableFrom(current, type2)) {
                    return current;
                }
                ClassNode currentNode = classes.get(current);
                if (currentNode == null) {
                    break;
                }
                current = currentNode.superName;
            }

            return "java/lang/Object";
        }

        private boolean isAssignableFrom(String type1, String type2) {
            if (type1.equals(type2)) {
                return true;
            }

            ClassNode class2 = classes.get(type2);
            if (class2 == null) {
                return false;
            }

            if (type1.equals(class2.superName)) {
                return true;
            }

            if (class2.interfaces != null && class2.interfaces.contains(type1)) {
                return true;
            }

            if (class2.superName != null) {
                return isAssignableFrom(type1, class2.superName);
            }

            return false;
        }
    }
}
