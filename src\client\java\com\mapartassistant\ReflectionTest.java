package com.mapartassistant;

import net.minecraft.client.gui.components.AbstractSelectionList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

public class ReflectionTest {
    public static void crashWithConstructors() {
        StringBuilder sb = new StringBuilder("CONSTRUCTORS FOR AbstractSelectionList:\n");
        for (Constructor<?> c : AbstractSelectionList.class.getConstructors()) {
            sb.append(c.getName()).append("(");
            for (Parameter p : c.getParameters()) {
                sb.append(p.getType().getSimpleName()).append(" ").append(p.getName()).append(", ");
            }
            sb.append(")\n");
        }
        throw new RuntimeException(sb.toString());
    }
}
