package me.katze225.transformer.impl;

import me.katze225.transformer.ITransformer;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;

public class ShuffleTransformer implements ITransformer {

    @Override
    public void modify(ClassNode classNode) {
        if (classNode == null) {
            return;
        }
        if (classNode.fields != null && classNode.fields.size() > 1) {
            Collections.shuffle(classNode.fields, RANDOM);
        }
        if (classNode.methods != null && classNode.methods.size() > 1) {
            Collections.shuffle(classNode.methods, RANDOM);
        }
    }
}
