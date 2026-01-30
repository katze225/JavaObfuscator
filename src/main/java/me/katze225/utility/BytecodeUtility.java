package me.katze225.utility;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.function.Consumer;

public class BytecodeUtility {
    public static MethodNode getMethod(ClassNode node, String name, String desc) {
        return node.methods.stream()
                           .filter(m -> m.name.equals(name))
                           .filter(m -> m.desc.equals(desc))
                           .findAny()
                           .orElse(null);
    }

    public static FieldNode getField(ClassNode node, String name, String desc) {
        return node.fields.stream()
                          .filter(m -> m.name.equals(name))
                          .filter(m -> m.desc.equals(desc))
                          .findAny()
                          .orElse(null);
    }

    public static <T extends AbstractInsnNode> void forEach(InsnList instructions,
                                                            Class<T> type,
                                                            Consumer<T> consumer) {
        AbstractInsnNode[] array = instructions.toArray();
        for (AbstractInsnNode node : array) {
            if (node.getClass() == type) {
                consumer.accept((T) node);
            }
        }
    }

    public static void forEach(InsnList instructions, Consumer<AbstractInsnNode> consumer) {
        forEach(instructions, AbstractInsnNode.class, consumer);
    }

    public static AbstractInsnNode newIntegerNode(int i) {
        if (i >= -1 && i <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + i);
        } else if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, i);
        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, i);
        } else {
            return new LdcInsnNode(i);
        }
    }
}
