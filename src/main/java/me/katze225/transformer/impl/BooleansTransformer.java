package me.katze225.transformer.impl;

import me.katze225.transformer.ITransformer;
import me.katze225.utility.StringUtility;
import me.katze225.utility.NodeUtility;
import me.katze225.utility.NumberUtility;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class BooleansTransformer implements ITransformer {
    private static final int LARGE_METHOD_LIMIT = 4000;
    private String prefix;

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void modify(ClassNode classNode) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        Map<String, Integer> privateStaticBooleans = new HashMap<>();
        List<FieldNode> fieldsToRemove = new ArrayList<>();

        for (FieldNode field : classNode.fields) {
            if ((field.access & ACC_PRIVATE) != 0 && (field.access & ACC_STATIC) != 0
                && field.desc.equals("Z") && field.value != null) {
                privateStaticBooleans.put(field.name, ((Integer) field.value));
                fieldsToRemove.add(field);
            }
        }

        boolean used = false;
        String MNAME = prefix + StringUtility.randomString(40);
        String desc = "(III)I";
        int xor = RANDOM.nextInt();
        String FNAME1 = StringUtility.randomString(40);
        
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) {
                continue;
            }
            if (method.instructions == null || method.instructions.size() == 0) {
                continue;
            }

            if (isMethodTooLarge(method)) {
                continue;
            }

            for (AbstractInsnNode insnNode : method.instructions.toArray()) {
                if (insnNode instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insnNode;
                    if (fieldInsn.getOpcode() == GETSTATIC && fieldInsn.owner.equals(classNode.name)
                        && privateStaticBooleans.containsKey(fieldInsn.name)) {
                        int value = privateStaticBooleans.get(fieldInsn.name);
                        used = true;
                        InsnList list = new InsnList();
                        int k2 = RANDOM.nextInt();
                        int k3 = RANDOM.nextInt();
                        list.add(new LdcInsnNode(value ^ (xor ^ k2)));
                        list.add(new LdcInsnNode(k2));
                        list.add(new LdcInsnNode(k3));
                        list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, MNAME, desc));
                        method.instructions.insert(fieldInsn, list);
                        method.instructions.remove(fieldInsn);
                    }
                } else if (isBooleanConstantInsn(insnNode)
                        && isBooleanContext(method, insnNode)) {
                    used = true;
                    InsnList list = new InsnList();
                    int k2 = RANDOM.nextInt();
                    int k3 = RANDOM.nextInt();
                    list.add(new LdcInsnNode(NumberUtility.getInt(insnNode) ^ (xor ^ k2)));
                    list.add(new LdcInsnNode(k2));
                    list.add(new LdcInsnNode(k3));
                    list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, MNAME, desc));
                    method.instructions.insert(insnNode, list);
                    method.instructions.remove(insnNode);
                }
            }
        }

        for (FieldNode field : fieldsToRemove) {
            classNode.fields.remove(field);
        }

        if(used) {
            MethodVisitor mv = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, MNAME, desc, null, null);
            mv.visitCode();
            mv.visitVarInsn(ILOAD, 0);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitInsn(IXOR);
            mv.visitFieldInsn(GETSTATIC, classNode.name, FNAME1, "I");
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(IXOR);
            mv.visitInsn(IXOR);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            classNode.fields.add(new FieldNode(ACC_STATIC | ACC_SYNTHETIC, FNAME1, "I", null, null));
            MethodNode clInit = NodeUtility.getMethod(classNode, "<clinit>");
            if (clInit == null) {
                clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
                classNode.methods.add(clInit);
            }
            if (clInit.instructions == null)
                clInit.instructions = new InsnList();

            InsnList instructions = new InsnList();
            int key = RANDOM.nextInt();
            instructions.add(new LdcInsnNode(xor ^ key));
            instructions.add(new LdcInsnNode(key));
            instructions.add(new InsnNode(IXOR));
            instructions.add(new FieldInsnNode(PUTSTATIC, classNode.name, FNAME1, "I"));

            if (clInit.instructions == null || clInit.instructions.getFirst() == null) {
                clInit.instructions.add(instructions);
                clInit.instructions.add(new InsnNode(Opcodes.RETURN));
            } else {
                clInit.instructions.insertBefore(clInit.instructions.getFirst(), instructions);
            }
        }
    }

    private static boolean isMethodTooLarge(MethodNode method) {
        if (method == null || method.instructions == null) {
            return false;
        }
        return method.instructions.size() > LARGE_METHOD_LIMIT;
    }

    private static boolean isBooleanContext(MethodNode method, AbstractInsnNode insnNode) {
        AbstractInsnNode next = nextMeaningful(insnNode);
        if (next == null) {
            return false;
        }
        if (next instanceof JumpInsnNode) {
            int op = next.getOpcode();
            return op == IFEQ || op == IFNE;
        }
        if (next instanceof FieldInsnNode) {
            FieldInsnNode fieldInsn = (FieldInsnNode) next;
            int op = next.getOpcode();
            return (op == PUTFIELD || op == PUTSTATIC) && "Z".equals(fieldInsn.desc);
        }
        if (next.getOpcode() == IRETURN) {
            return Type.getReturnType(method.desc).getSort() == Type.BOOLEAN;
        }
        return false;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        AbstractInsnNode current = node.getNext();
        while (current != null && isSkippable(current)) {
            current = current.getNext();
        }
        return current;
    }

    private static boolean isSkippable(AbstractInsnNode node) {
        return node instanceof LabelNode
                || node instanceof LineNumberNode
                || node instanceof FrameNode;
    }

    private static boolean isBooleanConstantInsn(AbstractInsnNode insnNode) {
        if (insnNode == null) {
            return false;
        }
        int op = insnNode.getOpcode();
        if (op == ICONST_0 || op == ICONST_1) {
            return true;
        }
        if (insnNode instanceof LdcInsnNode && ((LdcInsnNode) insnNode).cst instanceof Integer) {
            int value = (Integer) ((LdcInsnNode) insnNode).cst;
            return value == 0 || value == 1;
        }
        if (insnNode instanceof IntInsnNode) {
            int value = ((IntInsnNode) insnNode).operand;
            return value == 0 || value == 1;
        }
        return false;
    }
}
