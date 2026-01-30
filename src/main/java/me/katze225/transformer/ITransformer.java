package me.katze225.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Random;

public interface ITransformer extends Opcodes {
    void modify(ClassNode classNode);
    default void setPrefix(String prefix) {};
    Random RANDOM = new Random();
}