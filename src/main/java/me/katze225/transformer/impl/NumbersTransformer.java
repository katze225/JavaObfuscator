package me.katze225.transformer.impl;

import me.katze225.transformer.ITransformer;
import me.katze225.utility.StringUtility;
import me.katze225.utility.NodeUtility;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NumbersTransformer implements ITransformer {
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

        Map<String, Object> privateStaticNumbers = new HashMap<>();
        List<FieldNode> fieldsToRemove = new ArrayList<>();

        for (FieldNode field : classNode.fields) {
            if ((field.access & ACC_PRIVATE) != 0 && (field.access & ACC_STATIC) != 0 && field.value != null) {
                if (field.desc.equals("I") && field.value instanceof Integer) {
                    privateStaticNumbers.put(field.name, field.value);
                    fieldsToRemove.add(field);
                } else if (field.desc.equals("J") && field.value instanceof Long) {
                    privateStaticNumbers.put(field.name, field.value);
                    fieldsToRemove.add(field);
                } else if (field.desc.equals("F") && field.value instanceof Float) {
                    privateStaticNumbers.put(field.name, field.value);
                    fieldsToRemove.add(field);
                } else if (field.desc.equals("D") && field.value instanceof Double) {
                    privateStaticNumbers.put(field.name, field.value);
                    fieldsToRemove.add(field);
                }
            }
        }

        AtomicBoolean used = new AtomicBoolean(false);
        String MNAME = prefix + StringUtility.randomString(40);
        String desc = "(IIII)I";
        int xor = RANDOM.nextInt();
        String FNAME1 = StringUtility.randomString(40);

        classNode.methods.stream().forEach(mn -> {
            if ((mn.access & Opcodes.ACC_SYNTHETIC) != 0) {
                return;
            }
            if (isMethodTooLarge(mn)) {
                return;
            }
            if (mn.instructions == null || mn.instructions.size() == 0) {
                return;
            }

            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if (fieldInsn.getOpcode() == GETSTATIC && fieldInsn.owner.equals(classNode.name)
                        && privateStaticNumbers.containsKey(fieldInsn.name)) {
                        Object value = privateStaticNumbers.get(fieldInsn.name);
                        InsnList insnList = new InsnList();

                        if (value instanceof Integer) {
                            int originalNum = (Integer) value;
                            used.set(true);
                            insnList = buildIntObfuscation(originalNum, classNode.name, MNAME, desc, xor);
                        } else if (value instanceof Long) {
                            long originalNum = (Long) value;
                            insnList = buildLongObfuscation(originalNum);
                        } else if (value instanceof Float) {
                            float originalNum = (Float) value;
                            int bits = Float.floatToIntBits(originalNum);
                            used.set(true);
                            insnList = buildIntObfuscation(bits, classNode.name, MNAME, desc, xor);
                            insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false));
                        } else if (value instanceof Double) {
                            double originalNum = (Double) value;
                            long bits = Double.doubleToLongBits(originalNum);
                            insnList = buildLongObfuscation(bits);
                            insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false));
                        }

                        mn.instructions.insert(fieldInsn, insnList);
                        mn.instructions.remove(fieldInsn);
                    }
                } else if (isIntInsn(insn)) {
                    try {
                        int originalNum = (getIntegerFromInsn(insn));
                        used.set(true);
                        InsnList insnList = buildIntObfuscation(originalNum, classNode.name, MNAME, desc, xor);
                        mn.instructions.insertBefore(insn, insnList);
                        mn.instructions.remove(insn);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                 } else if (isLongInsn(insn)) {
                     try {
                     long originalNum = getLongFromInsn(insn);
                     InsnList insnList = buildLongObfuscation(originalNum);
                     mn.instructions.insertBefore(insn, insnList);
                     mn.instructions.remove(insn);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                 } else if (isFloatInsn(insn)) {
                     try {
                         float originalNum = getFloatFromInsn(insn);
                         int bits = Float.floatToIntBits(originalNum);
                         used.set(true);
                         InsnList insnList = buildIntObfuscation(bits, classNode.name, MNAME, desc, xor);
                         insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false));
                         mn.instructions.insertBefore(insn, insnList);
                         mn.instructions.remove(insn);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 } else if (isDoubleInsn(insn)) {
                     try {
                         double originalNum = getDoubleFromInsn(insn);
                         long bits = Double.doubleToLongBits(originalNum);
                         InsnList insnList = buildLongObfuscation(bits);
                         insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false));
                         mn.instructions.insertBefore(insn, insnList);
                         mn.instructions.remove(insn);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 }
             }
         });

        for (FieldNode field : fieldsToRemove) {
            classNode.fields.remove(field);
        }

        if(used.get()) {
            MethodVisitor mv = classNode.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, MNAME, desc, null, null);
            mv.visitCode();
            mv.visitVarInsn(ILOAD, 2);
            mv.visitVarInsn(ILOAD, 3);
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 0);
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 0);
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(IXOR);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(IXOR);
            mv.visitFieldInsn(GETSTATIC, classNode.name, FNAME1, "I");
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

    public static boolean isIntInsn(AbstractInsnNode insn) {
        if (insn == null) {
            return false;
        }
        int opcode = insn.getOpcode();
        return ((opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
                || opcode == Opcodes.BIPUSH
                || opcode == Opcodes.SIPUSH
                || (insn instanceof LdcInsnNode
                && ((LdcInsnNode) insn).cst instanceof Integer));
    }

    public static boolean isLongInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return (opcode == Opcodes.LCONST_0
                || opcode == Opcodes.LCONST_1
                || (insn instanceof LdcInsnNode
                && ((LdcInsnNode) insn).cst instanceof Long));
    }

    public static boolean isFloatInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
                || (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float);
    }

    public static boolean isDoubleInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return (opcode >= Opcodes.DCONST_0 && opcode <= Opcodes.DCONST_1)
                || (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double);
    }

    public static int getIntegerFromInsn(AbstractInsnNode insn) throws Exception {
        int opcode = insn.getOpcode();

        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return opcode - 3;
        } else if (insn instanceof IntInsnNode
                && insn.getOpcode() != Opcodes.NEWARRAY) {
            return ((IntInsnNode) insn).operand;
        } else if (insn instanceof LdcInsnNode
                && ((LdcInsnNode) insn).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) insn).cst;
        }

        throw new Exception("Unexpected instruction");
    }

    public static long getLongFromInsn(AbstractInsnNode insn) throws Exception {
        int opcode = insn.getOpcode();

        if (opcode >= Opcodes.LCONST_0 && opcode <= Opcodes.LCONST_1) {
            return opcode - 9;
        } else if (insn instanceof LdcInsnNode
                && ((LdcInsnNode) insn).cst instanceof Long) {
            return (Long) ((LdcInsnNode) insn).cst;
        }

        throw new Exception("Unexpected instruction");
    }

    private static InsnList buildIntObfuscation(int originalNum, String owner, String mname,
            String desc, int xor) {
        int value1 = RANDOM.nextInt();
        int value2 = originalNum ^ value1;
        InsnList insnList = new InsnList();
        int dummy = RANDOM.nextInt();
        insnList.add(new LdcInsnNode(value1));
        insnList.add(new LdcInsnNode(dummy));
        int k2 = RANDOM.nextInt();
        int k3 = RANDOM.nextInt();
        insnList.add(new LdcInsnNode(k3));
        insnList.add(new LdcInsnNode((value2 ^ k3) ^ (xor ^ k2)));
        insnList.add(new LdcInsnNode(k2 ^ k3));
        insnList.add(new MethodInsnNode(INVOKESTATIC, owner, mname, desc));
        insnList.add(new InsnNode(IXOR));
        return insnList;
    }

    private static InsnList buildLongObfuscation(long originalNum) {
        long value1 = RANDOM.nextLong();
        long value2 = originalNum ^ value1;
        InsnList insnList = new InsnList();
        insnList.add(new LdcInsnNode(value1));
        insnList.add(new LdcInsnNode(value2));
        insnList.add(new InsnNode(LXOR));
        return insnList;
    }

    public static float getFloatFromInsn(AbstractInsnNode insn) throws Exception {
        int opcode = insn.getOpcode();
        if (opcode == Opcodes.FCONST_0) return 0F;
        if (opcode == Opcodes.FCONST_1) return 1F;
        if (opcode == Opcodes.FCONST_2) return 2F;
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Float f) return f;
        throw new Exception("Unexpected instruction");
    }

    public static double getDoubleFromInsn(AbstractInsnNode insn) throws Exception {
        int opcode = insn.getOpcode();
        if (opcode == Opcodes.DCONST_0) return 0D;
        if (opcode == Opcodes.DCONST_1) return 1D;
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Double d) return d;
        throw new Exception("Unexpected instruction");
    }
}
