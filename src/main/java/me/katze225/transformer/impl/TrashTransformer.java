package me.katze225.transformer.impl;

import me.katze225.transformer.ITransformer;
import me.katze225.utility.BytecodeUtility;
import me.katze225.utility.StringUtility;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class TrashTransformer implements ITransformer {
    private String prefix;

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void modify(ClassNode classNode) {
        if ((classNode.access & ACC_INTERFACE) != 0) {
            return;
        }

        List<MethodNode> noArgMethods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
                continue;
            }
            // Check if it's a method with no arguments
            if (method.desc.startsWith("()")) {
                noArgMethods.add(method);
            }
        }

        for (MethodNode method : classNode.methods) {
            if (method.instructions == null || method.instructions.size() == 0) {
                continue;
            }
            if ((method.access & ACC_ABSTRACT) != 0 || (method.access & ACC_NATIVE) != 0) {
                continue;
            }

            int lineCount = countLines(method);
            if (lineCount < 4) {
                continue;
            }

            int injections;
            if (lineCount < 8) {
                injections = 1;
            } else if (lineCount <= 15) {
                injections = 2 + RANDOM.nextInt(2); // 2-3
            } else {
                injections = 3;
            }

            // Find possible injection points
            List<AbstractInsnNode> targetInsns = new ArrayList<>();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (isMeaningful(insn)) {
                    targetInsns.add(insn);
                }
            }

            if (targetInsns.isEmpty()) {
                continue;
            }

            for (int i = 0; i < injections; i++) {
                AbstractInsnNode point = targetInsns.get(RANDOM.nextInt(targetInsns.size()));
                InsnList junk = createJunk(classNode, method, noArgMethods);
                method.instructions.insertBefore(point, junk);
            }
        }
    }

    private int countLines(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (isMeaningful(insn)) {
                count++;
            }
        }
        return count;
    }

    private boolean isMeaningful(AbstractInsnNode insn) {
        return !(insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode);
    }

    private InsnList createJunk(ClassNode classNode, MethodNode method, List<MethodNode> noArgMethods) {
        InsnList list = new InsnList();
        int pattern = RANDOM.nextInt(2);

        if (pattern == 0 && !noArgMethods.isEmpty()) {
            // Pattern 1: Impossible condition (never executed)
            // if (a < b) { trashMethod1(); } where a is much larger than b
            int a = 100000 + RANDOM.nextInt(1000000);
            int b = RANDOM.nextInt(10000);
            
            LabelNode endLabel = new LabelNode();
            list.add(BytecodeUtility.newIntegerNode(a));
            list.add(BytecodeUtility.newIntegerNode(b));
            // if a > b then jump to end (always jumps)
            list.add(new JumpInsnNode(IF_ICMPGT, endLabel));

            MethodNode target = noArgMethods.get(RANDOM.nextInt(noArgMethods.size()));
            boolean isStatic = (target.access & ACC_STATIC) != 0;
            boolean currentIsStatic = (method.access & ACC_STATIC) != 0;

            if (isStatic) {
                list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, target.name, target.desc, false));
                if (!target.desc.endsWith(")V")) {
                    list.add(new InsnNode(getPopOpcode(target.desc)));
                }
            } else if (!currentIsStatic) {
                list.add(new VarInsnNode(ALOAD, 0)); // push 'this'
                list.add(new MethodInsnNode(INVOKEVIRTUAL, classNode.name, target.name, target.desc, false));
                if (!target.desc.endsWith(")V")) {
                    list.add(new InsnNode(getPopOpcode(target.desc)));
                }
            } else {
                list.add(BytecodeUtility.newIntegerNode(RANDOM.nextInt(1000000)));
                list.add(BytecodeUtility.newIntegerNode(RANDOM.nextInt(1000000)));
                list.add(new InsnNode(IMUL));
                list.add(new InsnNode(POP));
            }
            list.add(endLabel);
        } else {
            // Pattern 2: Junk loop with junk variable name
            int valAa = 100000 + RANDOM.nextInt(1000000);
            int valLimit = 10000 + RANDOM.nextInt(10000);
            int valBreak = valLimit - 1000 - RANDOM.nextInt(1000);
            
            int localId = method.maxLocals++;
            String varName = prefix + StringUtility.randomString(10);
            
            LabelNode startWhile = new LabelNode();
            LabelNode endWhile = new LabelNode();
            LabelNode ifEnd = new LabelNode();
            LabelNode startVar = new LabelNode();
            LabelNode endVar = new LabelNode();

            list.add(startVar);
            list.add(BytecodeUtility.newIntegerNode(valAa));
            list.add(new VarInsnNode(ISTORE, localId));
            
            list.add(new VarInsnNode(ILOAD, localId));
            list.add(BytecodeUtility.newIntegerNode(valLimit));
            list.add(new JumpInsnNode(IF_ICMPLE, ifEnd));
            
            list.add(startWhile);
            list.add(new VarInsnNode(ILOAD, localId));
            list.add(BytecodeUtility.newIntegerNode(valBreak));
            // if local > valBreak, break
            list.add(new JumpInsnNode(IF_ICMPGT, endWhile));
            list.add(new JumpInsnNode(GOTO, startWhile));
            
            list.add(endWhile);
            list.add(ifEnd);
            list.add(endVar);

            if (method.localVariables == null) {
                method.localVariables = new ArrayList<>();
            }
            method.localVariables.add(new LocalVariableNode(varName, "I", null, startVar, endVar, localId));
        }
        return list;
    }

    private int getPopOpcode(String desc) {
        if (desc.endsWith("D") || desc.endsWith("J")) {
            return POP2;
        }
        return POP;
    }
}
