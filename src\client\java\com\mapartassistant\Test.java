package com.mapartassistant;

import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Test {
    public static void main(String[] args) {
        System.out.println("Methods in ContainerObjectSelectionList.Entry:");
        for (Method m : ContainerObjectSelectionList.Entry.class.getMethods()) {
            System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getName() + "(...)");
        }
    }
}
