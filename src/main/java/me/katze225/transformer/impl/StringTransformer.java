package me.katze225.transformer.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import me.katze225.utility.NodeUtility;


import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import me.katze225.utility.BytecodeUtility;
import me.katze225.utility.StringUtility;
import me.katze225.transformer.ITransformer;

public class StringTransformer implements ITransformer {
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

	private static String XOR(int i, int j, String string, int k, int l, char[] s) {
		StringBuilder sb = new StringBuilder();
		int i1 = 0;
		int mix = (i * 31) ^ (j * 17) ^ (k * 13) ^ (l * 7);
		for(char c : string.toCharArray()) {
			int idx = Math.floorMod(i1 + mix, s.length);
			sb.append((char)((((c ^ s[idx]) ^ (i ^ k + i1)) ^ j) ^ l));
			i1++;
		}
		return sb.toString();
	}
	
	private static String EncryptKey(String s, char[] b) {
		 final char[] charArray = s.toCharArray();
	        final int i = charArray.length;
	        for (int n = 0; i > n; ++n) {
	            final int n2 = n;
	            final char c = charArray[n2];
	            char c2 = '\0';
	            switch (n % 7) {
	                case 0: {
	                    c2 = b[0];
	                    break;
	                }
	                case 1: {
	                    c2 = b[1];
	                    break;
	                }
	                case 2: {
	                    c2 = b[2];
	                    break;
	                }
	                case 3: {
	                    c2 = b[3];
	                    break;
	                }
	                case 4: {
	                    c2 = b[4];
	                    break;
	                }
	                case 5: {
	                    c2 = b[5];
	                    break;
	                }
	                default: {
	                    c2 = b[6];
	                    break;
	                }
	            }
	            charArray[n2] = (char)(c ^ c2);
	        }
	        return new String(charArray).intern();
	}
	
	
	@Override
	public void modify(ClassNode node) {
        if ((node.access & ACC_INTERFACE) != 0)
            return;
	try {
		if(node.methods != null && node.methods.size() > 0) {
			
			Map<String, String> privateStaticStrings = new HashMap<>();
			List<FieldNode> fieldsToRemove = new ArrayList<>();
			
			for (FieldNode field : node.fields) {
				if ((field.access & ACC_PRIVATE) != 0 && (field.access & ACC_STATIC) != 0 
					&& field.desc.equals("Ljava/lang/String;") && field.value instanceof String) {
					privateStaticStrings.put(field.name, (String) field.value);
					fieldsToRemove.add(field);
				}
			}
			
			String key = prefix + StringUtility.randomString(100);
			Random ran = RANDOM;
			char[] key2 = new char[] {(char) ran.nextInt(126), (char) ran.nextInt(126), (char) ran.nextInt(126)
					, (char) ran.nextInt(126), (char) ran.nextInt(126), (char) ran.nextInt(126), (char) ran.nextInt(126)};
			AtomicBoolean used = new AtomicBoolean(false);
			
			String NAME3 = prefix + StringUtility.randomString(40);
			
			{
				FieldVisitor fieldVisitor = node.visitField(ACC_STATIC | ACC_SYNTHETIC, NAME3, "[C", null, null);
				fieldVisitor.visitEnd();
				}
			
			String name = StringUtility.randomString(40);
			StringBuilder descBuilder = new StringBuilder("(");
			int dummyBefore = RANDOM.nextInt(4);
			for (int i = 0; i < dummyBefore; i++) {
				descBuilder.append("I");
			}
			int baseParamStart = dummyBefore;
			descBuilder.append("IILjava/lang/String;II");
			int dummyAfter = RANDOM.nextInt(4);
			for (int i = 0; i < dummyAfter; i++) {
				descBuilder.append("I");
			}
			descBuilder.append(")Ljava/lang/String;");
			String descriptor = descBuilder.toString();
			
			for(MethodNode mn : node.methods) {
				if (mn.name.equals(name) && descriptor.equals(mn.desc)) {
					continue;
				}
                if (isMethodTooLarge(mn)) {
                    continue;
                }
				if (mn.instructions == null || mn.instructions.size() == 0) {
					continue;
				}
				
				BytecodeUtility.forEach(mn.instructions, insn -> {
					if (insn instanceof FieldInsnNode) {
						FieldInsnNode fieldInsn = (FieldInsnNode) insn;
						if (fieldInsn.getOpcode() == GETSTATIC && fieldInsn.owner.equals(node.name) 
							&& privateStaticStrings.containsKey(fieldInsn.name)) {
							String s = privateStaticStrings.get(fieldInsn.name);
							int k1 = RANDOM.nextInt();
							int k2 = RANDOM.nextInt();
							int k3 = RANDOM.nextInt();
							int k4 = RANDOM.nextInt();
							InsnList il = new InsnList();
							for (int i = 0; i < dummyBefore; i++) {
								il.add(new LdcInsnNode(RANDOM.nextInt()));
							}
							il.add(new LdcInsnNode(k1));
							il.add(new LdcInsnNode(k2));
							il.add(new LdcInsnNode(XOR(k1, k2, s, k3, k4, key.toCharArray())));
							il.add(new LdcInsnNode(k3));
							il.add(new LdcInsnNode(k4));
							for (int i = 0; i < dummyAfter; i++) {
								il.add(new LdcInsnNode(RANDOM.nextInt()));
							}
							il.add(new MethodInsnNode(INVOKESTATIC, node.name, name, descriptor, false));
							mn.instructions.insert(fieldInsn, il);
							mn.instructions.remove(fieldInsn);
							used.set(true);
						}
					}
				});
				
				BytecodeUtility.<LdcInsnNode>forEach(mn.instructions, LdcInsnNode.class, ldc -> {
					if (ldc.cst instanceof String) {
						int k1 = RANDOM.nextInt();
						int k2 = RANDOM.nextInt();
						int k3 = RANDOM.nextInt();
						int k4 = RANDOM.nextInt();
						String s = (String)ldc.cst;
						InsnList il = new InsnList();
						for (int i = 0; i < dummyBefore; i++) {
							il.add(new LdcInsnNode(RANDOM.nextInt()));
						}
						il.add(new LdcInsnNode(k1));
						il.add(new LdcInsnNode(k2));
						il.add(new LdcInsnNode(XOR(k1, k2, s, k3, k4, key.toCharArray())));
						il.add(new LdcInsnNode(k3));
						il.add(new LdcInsnNode(k4));
						for (int i = 0; i < dummyAfter; i++) {
							il.add(new LdcInsnNode(RANDOM.nextInt()));
						}
						il.add(new MethodInsnNode(INVOKESTATIC, node.name, name, descriptor, false));
						mn.instructions.insert(ldc, il);
						mn.instructions.remove(ldc);
						used.set(true);
					}
				});
			}
			
			for (FieldNode field : fieldsToRemove) {
				node.fields.remove(field);
			}

			if (!used.get()) {
				return;
			}
			
			{
			MethodVisitor methodVisitor = node.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, name, descriptor, null, null);
			methodVisitor.visitCode();
			Label label0 = new Label();
			methodVisitor.visitLabel(label0);
			methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, baseParamStart + 5);
			Label label1 = new Label();
			methodVisitor.visitLabel(label1);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 6);
			Label label1a = new Label();
			methodVisitor.visitLabel(label1a);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 0);
			methodVisitor.visitIntInsn(BIPUSH, 31);
			methodVisitor.visitInsn(IMUL);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 1);
			methodVisitor.visitIntInsn(BIPUSH, 17);
			methodVisitor.visitInsn(IMUL);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 3);
			methodVisitor.visitIntInsn(BIPUSH, 13);
			methodVisitor.visitInsn(IMUL);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 4);
			methodVisitor.visitIntInsn(BIPUSH, 7);
			methodVisitor.visitInsn(IMUL);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 11);
			Label label2 = new Label();
			methodVisitor.visitLabel(label2);
			methodVisitor.visitVarInsn(ALOAD, baseParamStart + 2);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ASTORE, baseParamStart + 10);
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 9);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 8);
			Label label3 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label3);
			Label label4 = new Label();
			methodVisitor.visitLabel(label4);
			methodVisitor.visitVarInsn(ALOAD, baseParamStart + 10);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 8);
			methodVisitor.visitInsn(CALOAD);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 7);
			Label label5 = new Label();
			methodVisitor.visitLabel(label5);
			methodVisitor.visitVarInsn(ALOAD, baseParamStart + 5);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 7);
			methodVisitor.visitFieldInsn(GETSTATIC, node.name, NAME3, "[C");
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 6);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 11);
			methodVisitor.visitInsn(IADD);
			methodVisitor.visitFieldInsn(GETSTATIC, node.name, NAME3, "[C");
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ISTORE, baseParamStart + 12);
			methodVisitor.visitInsn(IREM);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 12);
			methodVisitor.visitInsn(IADD);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 12);
			methodVisitor.visitInsn(IREM);
			methodVisitor.visitInsn(CALOAD);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 0);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 3);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 6);
			methodVisitor.visitInsn(IADD);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 1);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 4);
			methodVisitor.visitInsn(IXOR);
			methodVisitor.visitInsn(I2C);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			methodVisitor.visitInsn(POP);
			Label label6 = new Label();
			methodVisitor.visitLabel(label6);
			methodVisitor.visitIincInsn(baseParamStart + 6, 1);
			Label label7 = new Label();
			methodVisitor.visitLabel(label7);
			methodVisitor.visitIincInsn(baseParamStart + 8, 1);
			methodVisitor.visitLabel(label3);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 8);
			methodVisitor.visitVarInsn(ILOAD, baseParamStart + 9);
			methodVisitor.visitJumpInsn(IF_ICMPLT, label4);
			Label label8 = new Label();
			methodVisitor.visitLabel(label8);
			methodVisitor.visitVarInsn(ALOAD, baseParamStart + 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			methodVisitor.visitInsn(ARETURN);
			Label label9 = new Label();
			methodVisitor.visitLabel(label9);
			methodVisitor.visitMaxs(6, baseParamStart + 13);
			methodVisitor.visitEnd();
		}
			{
				{
			    	//MethodVisitor methodVisitor = node.visitMethod(ACC_PRIVATE | ACC_STATIC, NAME2, "()V", null, null);
					MethodNode methodVisitor = new MethodNode();
					methodVisitor.visitCode();
					methodVisitor.visitLdcInsn(EncryptKey(key, key2));
					methodVisitor.visitInsn(ICONST_M1);
					Label label0 = new Label();
					methodVisitor.visitJumpInsn(GOTO, label0);
					Label label1 = new Label();
					methodVisitor.visitLabel(label1);
					methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/String"});
					
					
					methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
					methodVisitor.visitFieldInsn(PUTSTATIC, node.name, NAME3, "[C");
					
				//	methodVisitor.visitVarInsn(ASTORE, 1);
					
					Label label2 = new Label();
					methodVisitor.visitJumpInsn(GOTO, label2);
					methodVisitor.visitLabel(label0);
					methodVisitor.visitFrame(Opcodes.F_FULL, 0, new Object[] {}, 2, new Object[] {"java/lang/String", Opcodes.INTEGER});
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
					methodVisitor.visitInsn(DUP);
					methodVisitor.visitInsn(ARRAYLENGTH);
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitInsn(ICONST_0);
					methodVisitor.visitVarInsn(ISTORE, 0);
					Label label3 = new Label();
					methodVisitor.visitJumpInsn(GOTO, label3);
					Label label4 = new Label();
					methodVisitor.visitLabel(label4);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 3, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C"});
					methodVisitor.visitInsn(DUP);
					methodVisitor.visitVarInsn(ILOAD, 0);
					Label label5 = new Label();
					methodVisitor.visitLabel(label5);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 5, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER});
					methodVisitor.visitInsn(DUP2);
					methodVisitor.visitInsn(CALOAD);
					methodVisitor.visitVarInsn(ILOAD, 0);
					methodVisitor.visitIntInsn(BIPUSH, 7);
					methodVisitor.visitInsn(IREM);
					Label label6 = new Label();
					Label label7 = new Label();
					Label label8 = new Label();
					Label label9 = new Label();
					Label label10 = new Label();
					Label label11 = new Label();
					Label label12 = new Label();
					methodVisitor.visitTableSwitchInsn(0, 5, label12, new Label[] { label6, label7, label8, label9, label10, label11 });
					methodVisitor.visitLabel(label6);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[0]);
					Label label13 = new Label();
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label7);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[1]);
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label8);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[2]);
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label9);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[3]);
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label10);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[4]);
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label11);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[5]);
					methodVisitor.visitJumpInsn(GOTO, label13);
					methodVisitor.visitLabel(label12);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 6, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitIntInsn(BIPUSH, key2[6]);
					methodVisitor.visitLabel(label13);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 7, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C", "[C", Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER});
					methodVisitor.visitInsn(IXOR);
					methodVisitor.visitInsn(I2C);
					methodVisitor.visitInsn(CASTORE);
					methodVisitor.visitIincInsn(0, 1);
					methodVisitor.visitLabel(label3);
					methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 3, new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, "[C"});
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitInsn(DUP_X1);
					methodVisitor.visitVarInsn(ILOAD, 0);
					methodVisitor.visitJumpInsn(IF_ICMPGT, label4);
					methodVisitor.visitTypeInsn(NEW, "java/lang/String");
					methodVisitor.visitInsn(DUP_X1);
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
					methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "intern", "()Ljava/lang/String;", false);
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitInsn(POP);
					methodVisitor.visitInsn(SWAP);
					methodVisitor.visitInsn(POP);
					methodVisitor.visitJumpInsn(GOTO, label1);
					methodVisitor.visitLabel(label2);
					methodVisitor.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
				//	methodVisitor.visitVarInsn(ALOAD, 1);
				//	methodVisitor.visitInsn(RETURN);
					
					 MethodNode clInit = NodeUtility.getMethod(node, "<clinit>");
		             if (clInit == null) {
		                 clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
		                 node.methods.add(clInit);
		             }
		             if (clInit.instructions == null)
		                 clInit.instructions = new InsnList();
		             
		             
		             if (clInit.instructions == null || clInit.instructions.getFirst() == null) {
		                 clInit.instructions.add(methodVisitor.instructions);
		                 clInit.instructions.add(new InsnNode(Opcodes.RETURN));
		             } else {
		                 clInit.instructions.insertBefore(clInit.instructions.getFirst(), methodVisitor.instructions);
		             }
					
					
				}
			}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
	}
}
