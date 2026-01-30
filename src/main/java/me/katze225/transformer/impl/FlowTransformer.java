package me.katze225.transformer.impl;

import me.katze225.transformer.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;


public class FlowTransformer implements ITransformer {
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
	@Override
	public void modify(ClassNode classNode) {
		if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
			return;
		}

		for (MethodNode method : classNode.methods) {
			if (method.instructions == null || method.instructions.size() == 0) {
				continue;
			}

			if (isMethodTooLarge(method)) {
				continue;
			}

			boolean isClinit = method.name.equals("<clinit>");

			for (AbstractInsnNode insnNode : method.instructions.toArray()) {
				Type returnType = Type.getReturnType(method.desc);
				if (!isClinit && returnType.getSort() == Type.BOOLEAN && isBooleanReturn(insnNode)) {
					AbstractInsnNode prev = previousMeaningful(insnNode);
					if (prev != null && isBooleanConstant(prev)) {
						int value = getBooleanValue(prev);
						InsnList replacement = createComplexBooleanExpression(value);
						method.instructions.insertBefore(prev, replacement);
						method.instructions.remove(prev);
					}
				}

				if (isIfComparisonInsn(insnNode)) {
					if (!isClinit) {
						AbstractInsnNode prev = previousMeaningful(insnNode);
						if (prev != null && isIntConstant(prev)) {
							int constantValue = getIntValue(prev);
							InsnList replacement = createComplexIntExpression(constantValue);
							method.instructions.insertBefore(prev, replacement);
							method.instructions.remove(prev);
						}
					}
				}

				if (!isClinit && isMethodInvocation(insnNode)) {
					MethodInsnNode methodInsn = (MethodInsnNode) insnNode;
					String methodName = methodInsn.name;
					Type[] argTypes = Type.getArgumentTypes(methodInsn.desc);
					
					if (methodName.equals("setConnectTimeout") 
						|| methodName.equals("setReadTimeout")
						|| methodName.equals("setTimeout")) {
						AbstractInsnNode prev = previousMeaningful(insnNode);
						if (prev != null && isIntConstant(prev)) {
							int constantValue = getIntValue(prev);
							if (constantValue > 0 && constantValue <= 30000) {
								InsnList replacement = createComplexIntExpression(constantValue);
								method.instructions.insertBefore(prev, replacement);
								method.instructions.remove(prev);
							}
						}
					}
					
					if (argTypes.length > 0 && argTypes.length <= 3) {
						AbstractInsnNode current = previousMeaningful(insnNode);
						
						for (int i = argTypes.length - 1; i >= 0 && current != null; i--) {
							if (argTypes[i].getSort() == Type.BOOLEAN && isBooleanConstant(current)) {
								int value = getBooleanValue(current);
								InsnList replacement = createComplexBooleanExpression(value);
								AbstractInsnNode next = previousMeaningful(current);
								method.instructions.insertBefore(current, replacement);
								method.instructions.remove(current);
								current = next;
							} else {
								current = previousMeaningful(current);
							}
						}
					}
				}

			}
		}
	}

	private boolean isBooleanReturn(AbstractInsnNode node) {
		return node != null && node.getOpcode() == IRETURN;
	}

	private AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
		AbstractInsnNode current = node.getPrevious();
		while (current != null && isSkippable(current)) {
			current = current.getPrevious();
		}
		return current;
	}

	private boolean isSkippable(AbstractInsnNode node) {
		return node instanceof LabelNode
				|| node instanceof LineNumberNode
				|| node instanceof FrameNode;
	}

	private boolean isBooleanConstant(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		if (op == ICONST_0 || op == ICONST_1) {
			return true;
		}
		if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer) {
			int value = (Integer) ((LdcInsnNode) node).cst;
			return value == 0 || value == 1;
		}
		return false;
	}

	private int getBooleanValue(AbstractInsnNode node) {
		int op = node.getOpcode();
		if (op == ICONST_0) {
			return 0;
		}
		if (op == ICONST_1) {
			return 1;
		}
		if (node instanceof LdcInsnNode) {
			return (Integer) ((LdcInsnNode) node).cst;
		}
		return 0;
	}

	private boolean isMethodInvocation(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		return op == INVOKEVIRTUAL || op == INVOKESPECIAL 
			|| op == INVOKESTATIC || op == INVOKEINTERFACE;
	}

	private boolean isNullCheckInsn(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		return op == IFNULL || op == IFNONNULL;
	}

	private boolean isObjectComparisonInsn(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		return op == IF_ACMPEQ || op == IF_ACMPNE;
	}

	private void invertCondition(MethodNode method, AbstractInsnNode jumpInsn) {
		JumpInsnNode jump = (JumpInsnNode) jumpInsn;
		LabelNode originalTarget = jump.label;
		
		if (!hasCodeBetween(jumpInsn, originalTarget)) {
			return;
		}
		
		LabelNode newElseLabel = new LabelNode();
		int invertedOp = getInvertedIntComparison(jump.getOpcode());
		JumpInsnNode newJump = new JumpInsnNode(invertedOp, newElseLabel);
		
		method.instructions.set(jump, newJump);
		method.instructions.insert(newJump, newElseLabel);
		method.instructions.insert(newElseLabel, new JumpInsnNode(GOTO, originalTarget));
	}

	private void invertNullCheck(MethodNode method, AbstractInsnNode jumpInsn) {
		JumpInsnNode jump = (JumpInsnNode) jumpInsn;
		LabelNode originalTarget = jump.label;
		
		if (!hasCodeBetween(jumpInsn, originalTarget)) {
			return;
		}
		
		LabelNode newElseLabel = new LabelNode();
		int invertedOp = jump.getOpcode() == IFNULL ? IFNONNULL : IFNULL;
		JumpInsnNode newJump = new JumpInsnNode(invertedOp, newElseLabel);
		
		method.instructions.set(jump, newJump);
		method.instructions.insert(newJump, newElseLabel);
		method.instructions.insert(newElseLabel, new JumpInsnNode(GOTO, originalTarget));
	}

	private void invertObjectComparison(MethodNode method, AbstractInsnNode jumpInsn) {
		JumpInsnNode jump = (JumpInsnNode) jumpInsn;
		LabelNode originalTarget = jump.label;
		
		if (!hasCodeBetween(jumpInsn, originalTarget)) {
			return;
		}
		
		LabelNode newElseLabel = new LabelNode();
		int invertedOp = jump.getOpcode() == IF_ACMPEQ ? IF_ACMPNE : IF_ACMPEQ;
		JumpInsnNode newJump = new JumpInsnNode(invertedOp, newElseLabel);
		
		method.instructions.set(jump, newJump);
		method.instructions.insert(newJump, newElseLabel);
		method.instructions.insert(newElseLabel, new JumpInsnNode(GOTO, originalTarget));
	}

	private boolean hasCodeBetween(AbstractInsnNode start, LabelNode target) {
		AbstractInsnNode current = start.getNext();
		while (current != null && current != target) {
			if (current.getOpcode() >= 0 && !(current instanceof LabelNode) 
				&& !(current instanceof LineNumberNode) && !(current instanceof FrameNode)) {
				return true;
			}
			current = current.getNext();
		}
		return false;
	}

	private int getInvertedIntComparison(int opcode) {
		switch (opcode) {
			case IF_ICMPEQ: return IF_ICMPNE;
			case IF_ICMPNE: return IF_ICMPEQ;
			case IF_ICMPLT: return IF_ICMPGE;
			case IF_ICMPGE: return IF_ICMPLT;
			case IF_ICMPGT: return IF_ICMPLE;
			case IF_ICMPLE: return IF_ICMPGT;
			case IFEQ: return IFNE;
			case IFNE: return IFEQ;
			case IFLT: return IFGE;
			case IFGE: return IFLT;
			case IFGT: return IFLE;
			case IFLE: return IFGT;
			default: return opcode;
		}
	}

	private boolean isIfComparisonInsn(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		return op == IF_ICMPEQ || op == IF_ICMPNE || op == IF_ICMPLT 
			|| op == IF_ICMPGE || op == IF_ICMPGT || op == IF_ICMPLE;
	}

	private boolean isIntConstant(AbstractInsnNode node) {
		if (node == null) {
			return false;
		}
		int op = node.getOpcode();
		if (op >= ICONST_M1 && op <= ICONST_5) {
			return true;
		}
		if (node instanceof IntInsnNode) {
			return true;
		}
		if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer) {
			return true;
		}
		return false;
	}

	private int getIntValue(AbstractInsnNode node) {
		int op = node.getOpcode();
		if (op >= ICONST_M1 && op <= ICONST_5) {
			return op - ICONST_0;
		}
		if (node instanceof IntInsnNode) {
			return ((IntInsnNode) node).operand;
		}
		if (node instanceof LdcInsnNode) {
			return (Integer) ((LdcInsnNode) node).cst;
		}
		return 0;
	}

	private InsnList createComplexIntExpression(int targetValue) {
		InsnList list = new InsnList();
		int variant = RANDOM.nextInt(6);
		int mask = RANDOM.nextInt();

		switch (variant) {
			case 0: {
				int a = RANDOM.nextInt(500) + 100;
				int b = targetValue - a;
				list.add(new LdcInsnNode(a ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(b ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IADD));
				break;
			}
			case 1: {
				int a = RANDOM.nextInt(500) + 100;
				int b = a - targetValue;
				list.add(new LdcInsnNode(a ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(b ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(ISUB));
				break;
			}
			case 2: {
				int xorKey = RANDOM.nextInt();
				int encrypted = targetValue ^ xorKey;
				list.add(new LdcInsnNode(encrypted ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(xorKey ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IXOR));
				break;
			}
			case 3: {
				int a = RANDOM.nextInt(1000) + 500;
				int b = RANDOM.nextInt(1000) + 500;
				int diff = a - b;
				int adjustment = targetValue - diff;
				list.add(new LdcInsnNode(a ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(b ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(ISUB));
				if (adjustment != 0) {
					list.add(new LdcInsnNode(adjustment ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
				}
				break;
			}
			case 4: {
				int a = RANDOM.nextInt(300) + 50;
				int b = RANDOM.nextInt(300) + 50;
				int c = RANDOM.nextInt(300) + 50;
				int result = a + b - c;
				int adjustment = targetValue - result;
				list.add(new LdcInsnNode(a ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(b ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IADD));
				list.add(new LdcInsnNode(c ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(ISUB));
				if (adjustment != 0) {
					list.add(new LdcInsnNode(adjustment ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
				}
				break;
			}
			case 5: {
				int a = RANDOM.nextInt(200) + 100;
				int b = RANDOM.nextInt(200) + 100;
				int xorKey = RANDOM.nextInt(500);
				int result = (a ^ xorKey) + (b ^ xorKey);
				int adjustment = targetValue - result;
				list.add(new LdcInsnNode(a ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(xorKey ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(b ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new LdcInsnNode(xorKey ^ mask));
				list.add(new LdcInsnNode(mask));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IXOR));
				list.add(new InsnNode(IADD));
				if (adjustment != 0) {
					list.add(new LdcInsnNode(adjustment ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
				}
				break;
			}
		}



		return list;
	}

	private InsnList createComplexBooleanExpression(int targetValue) {
		InsnList list = new InsnList();
		int variant = RANDOM.nextInt(8);
		int mask = RANDOM.nextInt();

		if (targetValue == 1) {
			switch (variant) {
				case 0: {
					int a = RANDOM.nextInt(1000) + 500;
					int b = RANDOM.nextInt(1000) + 500;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
					list.add(new LdcInsnNode((a + b - 1) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPGT, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 1: {
					int a = RANDOM.nextInt(500) + 100;
					int b = RANDOM.nextInt(500) + 100;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode((a * b) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IDIV));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 2: {
					int a = RANDOM.nextInt(100) + 50;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IFEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 3: {
					int a = RANDOM.nextInt(100) + 10;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IMUL));
					list.add(new LdcInsnNode((a * a) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 4: {
					int a = RANDOM.nextInt(1000) + 100;
					int b = RANDOM.nextInt(1000) + 100;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(ISUB));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 5: {
					int a = RANDOM.nextInt(100) + 10;
					int b = a + 1;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPGT, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 6: {
					int a = RANDOM.nextInt(50) + 5;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(2 ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IMUL));
					list.add(new LdcInsnNode(2 ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IREM));
					list.add(new JumpInsnNode(IFEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 7: {
					int a = RANDOM.nextInt(100) + 10;
					int b = RANDOM.nextInt(100) + 10;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode((a | b) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
			}
		} else {
			switch (variant) {
				case 0: {
					int a = RANDOM.nextInt(1000) + 500;
					int b = RANDOM.nextInt(1000) + 500;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
					list.add(new LdcInsnNode((a + b + 1) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPGT, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 1: {
					int a = RANDOM.nextInt(500) + 100;
					int b = RANDOM.nextInt(500) + 100;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode((a * b) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IDIV));
					list.add(new LdcInsnNode((b + 1) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 2: {
					int a = RANDOM.nextInt(100) + 50;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IFNE, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 3: {
					int a = RANDOM.nextInt(100) + 10;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IMUL));
					list.add(new LdcInsnNode((a * a + 1) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 4: {
					int a = RANDOM.nextInt(1000) + 100;
					int b = RANDOM.nextInt(1000) + 100;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(ISUB));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IADD));
					list.add(new LdcInsnNode((a + 1) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 5: {
					int a = RANDOM.nextInt(100) + 10;
					int b = a - 1;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new JumpInsnNode(IF_ICMPGT, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 6: {
					int a = RANDOM.nextInt(50) + 5;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(2 ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IMUL));
					list.add(new InsnNode(ICONST_1));
					list.add(new InsnNode(IADD));
					list.add(new LdcInsnNode(2 ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IREM));
					list.add(new JumpInsnNode(IFEQ, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
				case 7: {
					int a = RANDOM.nextInt(100) + 10;
					int b = RANDOM.nextInt(100) + 10;
					LabelNode trueLabel = new LabelNode();
					LabelNode endLabel = new LabelNode();
					list.add(new LdcInsnNode((a | b) ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(a ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new LdcInsnNode(b ^ mask));
					list.add(new LdcInsnNode(mask));
					list.add(new InsnNode(IXOR));
					list.add(new InsnNode(IOR));
					list.add(new JumpInsnNode(IF_ICMPNE, trueLabel));
					list.add(new InsnNode(ICONST_0));
					list.add(new JumpInsnNode(GOTO, endLabel));
					list.add(trueLabel);
					list.add(new InsnNode(ICONST_1));
					list.add(endLabel);
					break;
				}
			}
		}

		return list;
	}
}
