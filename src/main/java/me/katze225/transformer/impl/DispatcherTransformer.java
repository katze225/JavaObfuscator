package me.katze225.transformer.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import me.katze225.transformer.ITransformer;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import me.katze225.utility.StringUtility;


public class DispatcherTransformer implements ITransformer {
    private String prefix;

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    private static final int LARGE_METHOD_LIMIT = 4000;

    private static boolean isMethodTooLarge(MethodNode method) {
        if (method == null || method.instructions == null) {
            return false;
        }
        return method.instructions.size() > LARGE_METHOD_LIMIT;
    }
	private static final String DISPATCH_DESC = "(I[Ljava/lang/Object;)Ljava/lang/Object;";

	@Override
	public void modify(org.objectweb.asm.tree.ClassNode classNode) {
		if (classNode == null || classNode.methods == null || classNode.methods.isEmpty()) {
			return;
		}
		if ((classNode.access & ACC_INTERFACE) != 0) {
			return;
		}
		Set<String> used = new HashSet<>();
		for (MethodNode method : classNode.methods) {
			used.add(methodKey(method.name, method.desc));
		}

		List<DispatchEntry> instanceEntries = new ArrayList<>();
		List<DispatchEntry> staticEntries = new ArrayList<>();
		Set<Integer> instanceKeys = new HashSet<>();
		Set<Integer> staticKeys = new HashSet<>();

		String instanceDispatchName = nextUniqueName(used, DISPATCH_DESC);
		used.add(methodKey(instanceDispatchName, DISPATCH_DESC));
		String staticDispatchName = nextUniqueName(used, DISPATCH_DESC);
		used.add(methodKey(staticDispatchName, DISPATCH_DESC));

		List<MethodNode> snapshot = new ArrayList<>(classNode.methods);
		for (MethodNode method : snapshot) {
			if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_BRIDGE | ACC_SYNTHETIC)) != 0) {
				continue;
			}
            if (isMethodTooLarge(method)) {
                continue;
            }
			if (method.name.startsWith("<")) {
				continue;
			}
			if (method.instructions == null || method.instructions.size() == 0) {
				continue;
			}

			String implName = nextUniqueName(used, method.desc);
			used.add(methodKey(implName, method.desc));

			MethodNode impl = new MethodNode();
			impl.access = toImplAccess(method.access);
			impl.name = implName;
			impl.desc = method.desc;
			impl.signature = method.signature;
			impl.exceptions = method.exceptions;
			impl.instructions = method.instructions;
			impl.tryCatchBlocks = method.tryCatchBlocks;
			impl.localVariables = method.localVariables;
			impl.maxLocals = method.maxLocals;
			impl.maxStack = method.maxStack;

			method.instructions = new InsnList();
			method.tryCatchBlocks = new ArrayList<>();
			method.visibleLocalVariableAnnotations = null;
			method.invisibleLocalVariableAnnotations = null;
			method.maxLocals = 0;
			method.maxStack = 0;

			boolean isStatic = (method.access & ACC_STATIC) != 0;
			int key = nextUniqueKey(isStatic ? staticKeys : instanceKeys);
			int mask = RANDOM.nextInt();
			String dispatchName = isStatic ? staticDispatchName : instanceDispatchName;
			buildStub(method, classNode.name, key, mask, isStatic, dispatchName);

			Type returnType = Type.getReturnType(method.desc);
			Type[] argTypes = Type.getArgumentTypes(method.desc);
			DispatchEntry entry = new DispatchEntry(key, implName, method.desc, returnType, argTypes, isStatic);
			if (isStatic) {
				staticEntries.add(entry);
			} else {
				instanceEntries.add(entry);
			}

			classNode.methods.add(impl);
		}

		if (!instanceEntries.isEmpty()) {
			MethodNode dispatch = createDispatch(instanceDispatchName, false, instanceEntries, classNode.name);
			classNode.methods.add(dispatch);
		}
		if (!staticEntries.isEmpty()) {
			MethodNode dispatch = createDispatch(staticDispatchName, true, staticEntries, classNode.name);
			classNode.methods.add(dispatch);
		}
	}

	private static MethodNode createDispatch(String name, boolean isStatic, List<DispatchEntry> entries, String owner) {
		int access = ACC_PRIVATE | ACC_SYNTHETIC | (isStatic ? ACC_STATIC : 0);
		MethodNode method = new MethodNode(access, name, DISPATCH_DESC, null, null);
		InsnList insn = method.instructions;

		List<DispatchEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingInt(entry -> entry.key));

		LabelNode defaultLabel = new LabelNode();
		LabelNode[] labels = new LabelNode[sorted.size()];
		int[] keys = new int[sorted.size()];
		for (int i = 0; i < sorted.size(); i++) {
			labels[i] = new LabelNode();
			keys[i] = sorted.get(i).key;
		}

		int keyIndex = isStatic ? 0 : 1;
		insn.add(new VarInsnNode(ILOAD, keyIndex));
		insn.add(new LookupSwitchInsnNode(defaultLabel, keys, labels));

		int argsIndex = isStatic ? 1 : 2;
		for (int i = 0; i < sorted.size(); i++) {
			DispatchEntry entry = sorted.get(i);
			insn.add(labels[i]);

			if (!entry.isStatic) {
				insn.add(new VarInsnNode(ALOAD, 0));
			}
			for (int argIndex = 0; argIndex < entry.argTypes.length; argIndex++) {
				Type argType = entry.argTypes[argIndex];
				insn.add(new VarInsnNode(ALOAD, argsIndex));
				insn.add(pushInt(argIndex));
				insn.add(new InsnNode(AALOAD));
				addUnbox(insn, argType);
			}

			int invokeOp = entry.isStatic ? INVOKESTATIC : INVOKESPECIAL;
			insn.add(new MethodInsnNode(invokeOp, owner, entry.implName, entry.desc, false));
			addBoxOrNullReturn(insn, entry.returnType);
		}

		insn.add(defaultLabel);
		insn.add(new InsnNode(ACONST_NULL));
		insn.add(new InsnNode(ARETURN));
		return method;
	}

	private static void buildStub(MethodNode method, String owner, int key, int mask, boolean isStatic,
			String dispatchName) {
		InsnList insn = method.instructions;
		Type returnType = Type.getReturnType(method.desc);
		Type[] argTypes = Type.getArgumentTypes(method.desc);

		if (!isStatic) {
			insn.add(new VarInsnNode(ALOAD, 0));
		}
		insn.add(new LdcInsnNode(key ^ mask));
		insn.add(new LdcInsnNode(mask));
		insn.add(new InsnNode(IXOR));
		insn.add(buildArgsArray(argTypes, isStatic));

		int invokeOp = isStatic ? INVOKESTATIC : INVOKESPECIAL;
		insn.add(new MethodInsnNode(invokeOp, owner, dispatchName, DISPATCH_DESC, false));

		addReturnUnbox(insn, returnType);
	}

	private static InsnList buildArgsArray(Type[] argTypes, boolean isStatic) {
		InsnList list = new InsnList();
		list.add(pushInt(argTypes.length));
		list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

		int varIndex = isStatic ? 0 : 1;
		for (int i = 0; i < argTypes.length; i++) {
			Type argType = argTypes[i];
			list.add(new InsnNode(DUP));
			list.add(pushInt(i));
			list.add(new VarInsnNode(argType.getOpcode(ILOAD), varIndex));
			addBox(list, argType);
			list.add(new InsnNode(AASTORE));
			varIndex += argType.getSize();
		}

		return list;
	}

	private static void addReturnUnbox(InsnList insn, Type returnType) {
		if (returnType.getSort() == Type.VOID) {
			insn.add(new InsnNode(POP));
			insn.add(new InsnNode(RETURN));
			return;
		}
		if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
			insn.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
			insn.add(new InsnNode(ARETURN));
			return;
		}
		String wrapper = wrapperInternalName(returnType);
		insn.add(new TypeInsnNode(CHECKCAST, wrapper));
		insn.add(new MethodInsnNode(INVOKEVIRTUAL, wrapper, unboxMethodName(returnType),
				"()" + returnType.getDescriptor(), false));
		insn.add(new InsnNode(returnType.getOpcode(IRETURN)));
	}

	private static void addBoxOrNullReturn(InsnList insn, Type returnType) {
		if (returnType.getSort() == Type.VOID) {
			insn.add(new InsnNode(ACONST_NULL));
			insn.add(new InsnNode(ARETURN));
			return;
		}
		if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
			insn.add(new InsnNode(ARETURN));
			return;
		}
		addBox(insn, returnType);
		insn.add(new InsnNode(ARETURN));
	}

	private static void addBox(InsnList insn, Type type) {
		if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
			return;
		}
		String wrapper = wrapperInternalName(type);
		String desc = "(" + type.getDescriptor() + ")L" + wrapper + ";";
		insn.add(new MethodInsnNode(INVOKESTATIC, wrapper, "valueOf", desc, false));
	}

	private static void addUnbox(InsnList insn, Type type) {
		if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
			insn.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
			return;
		}
		String wrapper = wrapperInternalName(type);
		insn.add(new TypeInsnNode(CHECKCAST, wrapper));
		insn.add(new MethodInsnNode(INVOKEVIRTUAL, wrapper, unboxMethodName(type),
				"()" + type.getDescriptor(), false));
	}

	private static String wrapperInternalName(Type type) {
		switch (type.getSort()) {
			case Type.BOOLEAN:
				return "java/lang/Boolean";
			case Type.BYTE:
				return "java/lang/Byte";
			case Type.CHAR:
				return "java/lang/Character";
			case Type.SHORT:
				return "java/lang/Short";
			case Type.INT:
				return "java/lang/Integer";
			case Type.LONG:
				return "java/lang/Long";
			case Type.FLOAT:
				return "java/lang/Float";
			case Type.DOUBLE:
				return "java/lang/Double";
			default:
				return "java/lang/Object";
		}
	}

	private static String unboxMethodName(Type type) {
		switch (type.getSort()) {
			case Type.BOOLEAN:
				return "booleanValue";
			case Type.BYTE:
				return "byteValue";
			case Type.CHAR:
				return "charValue";
			case Type.SHORT:
				return "shortValue";
			case Type.INT:
				return "intValue";
			case Type.LONG:
				return "longValue";
			case Type.FLOAT:
				return "floatValue";
			case Type.DOUBLE:
				return "doubleValue";
			default:
				return "toString";
		}
	}

	private static AbstractInsnNode pushInt(int value) {
		return new LdcInsnNode(value);
	}

	private int toImplAccess(int original) {
		int flags = original & (ACC_STATIC | ACC_BRIDGE | ACC_VARARGS | ACC_STRICT | ACC_FINAL);
		return flags | ACC_PRIVATE | ACC_SYNTHETIC;
	}

	private String nextUniqueName(Set<String> used, String desc) {
		String name;
		do {
			name = prefix + StringUtility.randomString(40);
		} while (used.contains(methodKey(name, desc)));
		return name;
	}

	private static String methodKey(String name, String desc) {
		return name + "|" + desc;
	}

	private static int nextUniqueKey(Set<Integer> used) {
		int key;
		do {
			key = RANDOM.nextInt(Short.MAX_VALUE);
		} while (used.contains(key));
		used.add(key);
		return key;
	}

	private static final class DispatchEntry {
		private final int key;
		private final String implName;
		private final String desc;
		private final Type returnType;
		private final Type[] argTypes;
		private final boolean isStatic;

		private DispatchEntry(int key, String implName, String desc, Type returnType, Type[] argTypes,
				boolean isStatic) {
			this.key = key;
			this.implName = implName;
			this.desc = desc;
			this.returnType = returnType;
			this.argTypes = argTypes;
			this.isStatic = isStatic;
		}
	}
}
